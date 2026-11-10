use crate::{devices::{Device, Devices}, tasks::Tasks};
use serde::{Deserialize, Serialize};
use std::{collections::BTreeMap, sync::Arc};
use tokio::sync::Mutex;

const RULE_VERSION: &str = "desktop-m3-1";

#[derive(Clone, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum Applicability { Applicable, NeedsChecks, Inapplicable }

#[derive(Clone, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum CheckStatus { Passed, Missing, Rejected }

#[derive(Clone, Deserialize, Serialize)]
pub struct Check { pub name: String, pub status: CheckStatus, pub reason: String }

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Tool { pub name: String, pub version: String, pub sha256: String }

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Host { pub platform: String, pub arch: String }

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Evidence {
    pub reference: String,
    pub host: Host,
    pub tool: Tool,
    // Exact device/build/resource combination validated by this evidence.
    pub conditions: BTreeMap<String, String>,
    pub maintenance_revision: u32,
}

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Scheme {
    pub id: String,
    pub title: String,
    pub source_revision: String,
    pub required_fields: Vec<String>,
    pub conditions: BTreeMap<String, String>,
    pub evidence: Vec<Evidence>,
    pub resources: Vec<Tool>,
    pub known_issues: Vec<String>,
    pub impact: String,
    pub impact_rank: u32,
    pub manual_steps: Vec<String>,
    pub preparation: Vec<String>,
}

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Candidate {
    pub scheme: Scheme,
    pub applicability: Applicability,
    pub checks: Vec<Check>,
    pub ranking_reason: String,
}

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Assessment {
    pub rule_version: String,
    pub device: Device,
    pub host: Host,
    pub candidates: Vec<Candidate>,
    pub recommended: Option<String>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExecutionSnapshot {
    pub rule_version: String,
    pub device: Device,
    pub host: Host,
    pub scheme: Scheme,
    pub evidence: Evidence,
}

fn freeze(previous: &Assessment, fresh: &Assessment, scheme_id: &str) -> Result<ExecutionSnapshot, String> {
    if previous.rule_version != fresh.rule_version || previous.device.id != fresh.device.id
        || previous.device.serial != fresh.device.serial || previous.device.transport_id != fresh.device.transport_id
        || previous.device.connection != fresh.device.connection || previous.device.mode.value != fresh.device.mode.value
        || previous.device.mode.status != fresh.device.mode.status
        || previous.host.platform != fresh.host.platform || previous.host.arch != fresh.host.arch {
        return Err("设备、模式、主机或规则已改变，旧推荐失效，请重新检查适配".into());
    }
    let before = previous.candidates.iter().find(|c| c.scheme.id == scheme_id).ok_or("方案不在原检查结果中")?;
    let candidate = fresh.candidates.iter().find(|c| c.scheme.id == scheme_id).ok_or("方案已撤回，旧推荐失效")?;
    if before.applicability != Applicability::Applicable || candidate.applicability != Applicability::Applicable {
        return Err("方案必要条件或主机/工具证据未全部通过，不能生成执行快照".into());
    }
    for key in candidate.scheme.required_fields.iter().map(String::as_str)
        .chain(["unlocked", "flashLocked", "vbmetaState", "hardwareSerial"]) {
        if value(&previous.device, key) != value(&fresh.device, key) {
            return Err(format!("{key} 已改变，旧推荐失效，请重新检查适配"));
        }
    }
    if serde_json::to_value(&before.scheme).expect("方案可序列化") != serde_json::to_value(&candidate.scheme).expect("方案可序列化") {
        return Err("方案、工具或资源已改变，旧推荐失效".into());
    }
    Ok(ExecutionSnapshot {
        rule_version: fresh.rule_version.clone(), device: fresh.device.clone(), host: fresh.host.clone(),
        scheme: candidate.scheme.clone(),
        evidence: matching_evidence(&candidate.scheme, &fresh.device, &fresh.host).expect("适用方案必须有精确证据").clone(),
    })
}

// This is a researched candidate, not an adopted or supported unlock route (M0b).
fn catalog() -> Vec<Scheme> {
    vec![Scheme {
        id: "mtkclient-seccfg-investigation".into(),
        title: "MTK seccfg · 调查候选（未验证）".into(),
        source_revision: "118a000a83ea31c4f427fe5225abfe964546e05e".into(),
        required_fields: ["model", "product", "soc", "build", "patch", "slot", "hwcode", "lkA", "lkB", "preloader", "da", "authorization"]
            .into_iter().map(String::from).collect(),
        conditions: BTreeMap::new(), evidence: vec![], resources: vec![],
        known_issues: vec!["尚无精确设备/固件和双平台锁定→解锁证据；patched BROM / V6 / LK RPMB 差异未验证".into()],
        impact: "可能丢失用户数据；取消或中断不表示设备回退".into(), impact_rank: 2,
        manual_steps: vec!["精确机型的按键、插拔、驱动/授权步骤尚待真机冻结".into()],
        preparation: vec!["用户启动后才能进入 BROM/preloader 或上传 DA；不在适配检查中执行".into(), "按真实 GPT 备份 seccfg、LK、preloader/boot 和 NV，并记录长度与 SHA-256".into()],
    }]
}

fn value<'a>(device: &'a Device, name: &str) -> Option<&'a str> {
    device.fields.get(name).filter(|field| field.status == "provided").map(|field| field.value.as_str())
}

fn check(name: &str, status: CheckStatus, reason: impl Into<String>) -> Check {
    Check { name: name.into(), status, reason: reason.into() }
}

fn lock_check(device: &Device) -> Check {
    if device.mode.status == "provided" && device.mode.value == "bootloaderFastboot" {
        match value(device, "unlocked") {
            Some("yes") => return check("锁状态", CheckStatus::Rejected, "Bootloader Fastboot 已确认解锁，不推荐重复解锁"),
            Some("no") => return check("锁状态", CheckStatus::Passed, "Bootloader Fastboot 已确认锁定"),
            _ => {}
        }
    }
    if value(device, "flashLocked") == Some("0") || value(device, "vbmetaState") == Some("unlocked") {
        return check("锁状态", CheckStatus::Rejected, "Android 属性报告已解锁，排除重复解锁；最终状态仍需权威 Fastboot 验证");
    }
    check("锁状态", CheckStatus::Missing, "未取得权威锁状态；未知不等于锁定。进入模式须由用户启动")
}

fn matching_evidence<'a>(scheme: &'a Scheme, device: &Device, host: &Host) -> Option<&'a Evidence> {
    scheme.evidence.iter().filter(|evidence| {
        evidence.host.platform == host.platform && evidence.host.arch == host.arch
            && evidence.tool.version == scheme.source_revision
            && scheme.required_fields.iter().all(|key| evidence.conditions.contains_key(key))
            && evidence.conditions.iter().all(|(key, expected)| value(device, key) == Some(expected.as_str()))
    }).max_by_key(|evidence| evidence.maintenance_revision)
}

fn evaluate(device: Device, host: Host, schemes: Vec<Scheme>) -> Assessment {
    let mut candidates: Vec<_> = schemes.into_iter().map(|scheme| {
        let mut checks = vec![lock_check(&device)];
        for key in &scheme.required_fields {
            checks.push(match value(&device, key) {
                None => check(key, CheckStatus::Missing, match device.fields.get(key) {
                    Some(field) => format!("{}：{}（{}）", field.status, field.value, field.source),
                    None => "当前阶段未采集；不通过重启、上传或写入静默补齐".into(),
                }),
                Some(actual) => match scheme.conditions.get(key) {
                    Some(expected) if actual != expected => check(key, CheckStatus::Rejected, format!("需要 {expected}，实际 {actual}")),
                    _ => check(key, CheckStatus::Passed, format!("已采集：{actual}")),
                },
            });
        }
        let evidence = matching_evidence(&scheme, &device, &host);
        checks.push(if let Some(evidence) = evidence {
            check("主机/工具/精确组合证据", CheckStatus::Passed, &evidence.reference)
        } else if scheme.evidence.is_empty() {
            check("主机/工具/精确组合证据", CheckStatus::Missing, "M0b 尚无正式支持证据，不参与推荐")
        } else if !scheme.evidence.iter().any(|e| e.host.platform == host.platform && e.host.arch == host.arch) {
            check("主机/工具/精确组合证据", CheckStatus::Rejected, format!("{} / {} 没有支持证据", host.platform, host.arch))
        } else {
            check("主机/工具/精确组合证据", CheckStatus::Missing, "当前设备、构建、资源或工具版本没有匹配的验证证据")
        });
        let applicability = if checks.iter().any(|c| c.status == CheckStatus::Rejected) { Applicability::Inapplicable }
            else if checks.iter().any(|c| c.status == CheckStatus::Missing) { Applicability::NeedsChecks }
            else { Applicability::Applicable };
        Candidate { scheme, applicability, checks, ranking_reason: "仅必要条件及当前主机/版本精确证据全部通过才推荐；按已知问题、影响、人工步骤、已验证维护版本、固定 ID 排序，不估算成功率".into() }
    }).collect();
    candidates.sort_by_key(|c| {
        let revision = matching_evidence(&c.scheme, &device, &host).map(|e| e.maintenance_revision);
        (c.applicability != Applicability::Applicable, std::cmp::Reverse(revision.is_some()), c.scheme.known_issues.len(), c.scheme.impact_rank,
            c.scheme.manual_steps.len(), std::cmp::Reverse(revision), c.scheme.id.clone())
    });
    let recommended = candidates.iter().find(|c| c.applicability == Applicability::Applicable).map(|c| c.scheme.id.clone());
    Assessment { rule_version: RULE_VERSION.into(), device, host, candidates, recommended }
}

pub struct Unlock { assessment: Mutex<Option<Assessment>> }

impl Unlock {
    pub fn new() -> Self { Self { assessment: Mutex::new(None) } }

    pub async fn inspect(&self, devices: &Arc<Devices>, tasks: &Arc<Tasks>, id: &str) -> Result<Assessment, String> {
        let device = devices.snapshot().await.devices.into_iter().find(|d| d.id == id).ok_or("设备已断开")?;
        let owner = format!("解锁适配采集:{id}");
        tasks.acquire(&device, &owner).await?;
        let result = devices.inspect(id).await;
        tasks.release(&owner).await;
        let assessment = evaluate(result?, Host { platform: std::env::consts::OS.into(), arch: std::env::consts::ARCH.into() }, catalog());
        *self.assessment.lock().await = Some(assessment.clone());
        Ok(assessment)
    }

    pub async fn recheck(&self, devices: &Arc<Devices>, tasks: &Arc<Tasks>, scheme_id: &str) -> Result<ExecutionSnapshot, String> {
        let previous = self.assessment.lock().await.clone().ok_or("请先检查适配")?;
        let fresh = self.inspect(devices, tasks, &previous.device.id).await?;
        freeze(&previous, &fresh, scheme_id)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::devices::Field;

    fn field(value: &str) -> Field {
        Field { status: "provided".into(), value: value.into(), source: "test fixture".into(), collected_at: 1 }
    }
    fn device() -> Device {
        Device { id: "fastboot:fixture".into(), serial: "fixture".into(), transport_id: None,
            connection: "fastboot".into(), mode: field("bootloaderFastboot"),
            fields: [("product".into(), field("test-product")), ("build".into(), field("test-build")), ("unlocked".into(), field("no"))].into(), observed_at: 1 }
    }
    fn host() -> Host { Host { platform: "test-os".into(), arch: "test-arch".into() } }
    fn scheme(id: &str) -> Scheme {
        Scheme { id: id.into(), title: "TEST FIXTURE ONLY".into(), source_revision: "fixture-revision".into(),
            required_fields: vec!["product".into(), "build".into()],
            conditions: [("product".into(), "test-product".into())].into(),
            evidence: vec![Evidence { reference: "test fixture, not support evidence".into(), host: host(),
                tool: Tool { name: "test".into(), version: "fixture-revision".into(), sha256: "fixture-digest".into() },
                conditions: [("product".into(), "test-product".into()), ("build".into(), "test-build".into())].into(), maintenance_revision: 1 }],
            resources: vec![], known_issues: vec![], impact: "fixture".into(), impact_rank: 1, manual_steps: vec![], preparation: vec![] }
    }
    #[test]
    fn applicable_and_exact_snapshot() {
        let a = evaluate(device(), host(), vec![scheme("a")]);
        assert_eq!(a.recommended.as_deref(), Some("a"));
        assert!(freeze(&a, &a, "a").is_ok());
    }
    #[test]
    fn mismatch_missing_and_unknown_lock_are_explained() {
        let mut d = device(); d.fields.insert("product".into(), field("other"));
        let a = evaluate(d, host(), vec![scheme("a")]);
        assert!(a.candidates[0].applicability == Applicability::Inapplicable);
        assert!(a.candidates[0].checks.iter().any(|c| c.reason.contains("实际 other")));
        let mut d = device(); d.fields.remove("build"); d.fields.remove("unlocked");
        let a = evaluate(d, host(), vec![scheme("a")]);
        assert!(a.candidates[0].applicability == Applicability::NeedsChecks);
        assert!(a.candidates[0].checks.iter().any(|c| c.reason.contains("未知不等于锁定")));
    }
    #[test]
    fn already_unlocked_and_unsupported_host_are_rejected() {
        let mut d = device(); d.fields.insert("unlocked".into(), field("yes"));
        assert!(evaluate(d, host(), vec![scheme("a")]).recommended.is_none());
        let mut h = host(); h.platform = "unsupported".into();
        let a = evaluate(device(), h, vec![scheme("a")]);
        assert!(a.candidates[0].applicability == Applicability::Inapplicable);
        assert!(a.candidates[0].checks.iter().any(|c| c.reason.contains("没有支持证据")));
    }
    #[test]
    fn stable_ranking_uses_issues_impact_steps_revision_then_id() {
        let a = scheme("a"); let mut b = scheme("b"); b.known_issues.push("fixture issue".into());
        let mut c = scheme("c"); c.impact_rank = 2;
        let mut d = scheme("d"); d.manual_steps.push("fixture manual step".into());
        let mut e = scheme("e"); e.evidence[0].maintenance_revision = 2;
        let f = scheme("f");
        let expected = ["e", "a", "f", "d", "c", "b"];
        for schemes in [vec![b.clone(), f.clone(), c.clone(), a.clone(), e.clone(), d.clone()], vec![d, e, a, c, f, b]] {
            let result = evaluate(device(), host(), schemes);
            assert_eq!(result.candidates.iter().map(|c| c.scheme.id.as_str()).collect::<Vec<_>>(), expected);
        }
    }
    #[test]
    fn changed_build_or_resources_invalidates_old_recommendation() {
        let old = evaluate(device(), host(), vec![scheme("a")]);
        let mut d = device(); d.fields.insert("build".into(), field("new-build"));
        let fresh = evaluate(d, host(), vec![scheme("a")]);
        assert!(freeze(&old, &fresh, "a").is_err());
        let mut changed = scheme("a"); changed.resources.push(Tool { name: "DA".into(), version: "2".into(), sha256: "changed".into() });
        assert!(freeze(&old, &evaluate(device(), host(), vec![changed]), "a").is_err());
    }
    #[test]
    fn production_catalog_never_claims_investigation_is_supported() {
        let result = evaluate(device(), host(), catalog());
        assert!(result.recommended.is_none());
        assert!(result.candidates.iter().all(|c| c.scheme.evidence.is_empty()));
    }
}

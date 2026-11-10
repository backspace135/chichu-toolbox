use serde::Serialize;
use tokio::sync::Mutex;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub version: &'static str,
    pub platform: &'static str,
    pub arch: &'static str,
    pub device_service: ServiceStatus,
    pub task_service: ServiceStatus,
}

#[derive(Clone, Serialize)]
#[serde(tag = "status", rename_all = "camelCase")]
pub enum ServiceStatus {
    NotImplemented { reason: &'static str },
}

pub struct Workspace(pub Mutex<Snapshot>);

impl Workspace {
    pub fn new() -> Self {
        Self(Mutex::new(Snapshot {
            version: env!("CARGO_PKG_VERSION"),
            platform: std::env::consts::OS,
            arch: std::env::consts::ARCH,
            device_service: ServiceStatus::NotImplemented {
                reason: "设备连接服务尚未接入（#19），当前未执行设备扫描。",
            },
            task_service: ServiceStatus::NotImplemented {
                reason: "任务执行与记录服务尚未接入（#20），当前不能创建任务。",
            },
        }))
    }
}

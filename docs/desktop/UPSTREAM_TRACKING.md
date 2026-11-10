# 桌面上游跟踪（#16）

要求来源：[#16](https://github.com/backspace135/chichu-toolbox/issues/16)、[#10 第 6 节](https://github.com/backspace135/chichu-toolbox/issues/10)。本实现是维护流程，不是客户端更新器，不运行上游安装脚本、pip 或设备操作。

## 文件与状态

| 文件/位置 | 含义 |
| --- | --- |
| `scripts/desktop-upstream/sources.json` | 显式仓库、分支、调查/集成分类和功能差异归属；不自动切换默认分支 |
| `scripts/desktop-upstream/initial-observation.json` | 本地只读 API 成功观测证据；不是候选或采用版本，也不作为线上状态的隐式兜底 |
| `desktop-upstream-state` 分支的 `scripts/desktop-upstream/state.json` | 自动维护的上次成功观测、最近尝试/错误、候选和下一批；分支不存在才首次初始化，权限/API 错误不当作首次 |
| `desktop-upstream/<source-id>` 分支的 `scripts/desktop-upstream/candidates/<source-id>.json`、`.md` | 精确候选快照、增量差异、相对采用基线差异、待审查维度和验证归属；每源只使用这个固定 head 分支查找/复用一个活跃 PR |
| `scripts/desktop-upstream/adopted.json` | 仅人工评审后维护的实际产品采用版本；目前为空，没有任何产品采用/支持声明 |

初始清单全部为 `investigation`：gjgjxweb 尚在 #14 盘点，mtkclient 与三个补充来源用于 #17 路线调查。调查模式只记录观测，不生成集成候选 PR。`gjgjxweb/master` 和其余四源的 `main` 来自真实 GitHub 仓库 API，不推测分支名称。后续 #14 的固定盘点清单仍是全功能验收来源。

确认真实集成后，在评审变更中把相应 `mode` 改成 `integration`，并维护归属。没有采用基线时，候选明确标为首次观测；不虚构增量或既有支持。已有采用记录的格式为：

```json
{
  "sources": {
    "source-id": {
      "snapshot": {"sha": "精确提交", "tags": {}, "releases": {}},
      "evidence": ["对应集成提交、功能清单和双平台/设备验收记录路径"]
    }
  }
}
```

`snapshot` 必须复制实际经过验收的完整候选快照（包括实际 Tag/Release/资源），示例中的空对象只说明结构。合并候选 PR 本身不会改写 adopted。首次集成和采用更新均需维护者核对实际集成、资源交付、许可和支持证据。

## 调度、权限与执行

`.github/workflows/desktop-upstream.yml` 每日 UTC 00:00（**09:00 JST**）计划运行，并提供手动入口；实际开始时间受 GitHub 调度影响。

1. `observe`：仅 `contents: read`。分页读取分支、Tag、Release 和 Release assets；保存带成功/失败状态的 artifact。保留 Tag 对象与剥离后的目标，可检测 annotated/lightweight Tag 移动。Release 保存元数据及资源 ID、digest（API 提供时）、大小、更新时间和 URL；下载次数不算资源变更。不下载或执行资源，不声称对 API 未提供的字节内容做独立哈希验证。
2. `publish`：仅 `contents: write`、`pull-requests: write`。即使某源观测失败，也先持久化其他成功观测及失败记录，再维护成功源候选 PR。API/权限失败令 job 明确失败。仓库需在 Actions 设置允许 GitHub Actions 创建 PR；禁止此权限时真实发布会失败，不能在本地证明其已获授权。
3. `candidate-checks`：工作流直接依赖 publish 输出的精确 head matrix，**不依赖 GITHUB_TOKEN 创建 PR 引发 `pull_request` 事件**。仅 `contents: read`、`checks: write`；在 GitHub 托管 Ubuntu runner 检查候选内容指纹、精确上游提交可读取性、完整树和许可文件存在性，并把 `desktop-upstream/static-candidate` check run 写到候选 head SHA。无变化/冻结的既有 PR 也显式重查，便于修复失败后手动重跑。

三个 job 都只从可信的工作流提交 checkout 脚本，关闭凭据持久化；不 checkout/执行候选源码。不使用签名、公证、发布 secrets、environment 或 self-hosted/真机 runner。静态检查成功只代表快照完整性和许可文件线索，**不是桌面构建、许可审定或双平台/设备功能验证**。实际适配器接入后，应在此显式增加对应无凭据构建/静态检查；设备/固件、授权和签名验收独立安排。全流程不自动 merge。

## 差异、失败和冻结

- 增量摘要以**上次成功观测**为起点，候选另以 **adopted 快照**为起点保留 GitHub compare 的提交与文件差异；无基线时明确记录首次观测。GitHub 文件列表达到 300 项时标记可能截断，需人工完整 diff。
- 每个候选有六项必审维度：设备/固件、步骤、依赖/资源、Windows/macOS、许可、全功能新增/变化。每项标记 required 和 issue 归属。文件/提交差异只用于发现线索，维护者须完成具体行为分析并把新增功能落到 #14 / #23–#30 或 #17 的后续排期；不凭提交标题自动推断支持。
- SHA、Tag 或 Release/资源任一快照变化产生新 identity；新候选的 validation 为空，不继承旧证据。静态检查证据在精确 PR head 的 check run 与本次 artifact；人工验收证据绑定 candidate identity，采用前必须审查。
- 手动运行时 `freeze` 填 source ID，可冻结当前存在的候选。冻结发生在观测前；当前 PR 文件及 head 保持不动，后续变化进入 state 的 `next_batch`。冻结候选经人工确认已采用后，把其完整 snapshot 和证据写入 adopted；下一次观测才会开启新批次。冻结不是采用，也不是丢弃新增功能。
- API/网络/权限/外部响应字段失败记录源和错误，保留该源 `last_success` 与 candidate，不报告无变化。`observe` 非零退出令整次 workflow 失败，同时 artifact/publish 保留可用成功数据。首次连 state 都读取失败时不初始化、不发布。
- 分支消失的 404 明确报错，不换分支。compare 的 diverged/behind 明确标为 `HISTORY REWRITTEN`；仍可保存成功观测用于调查，采用值永不被自动覆盖。旧 SHA 不可 compare 则本次失败并保留成功值。
- 同源多个活跃 PR 是显式错误；PR 发布失败不丢失此前已保存的观测。关闭但未采用的候选如仍需要集成，下次可重新开同源 PR；维护者应在清单/采用记录中表达真实决定。

## 本地验证与上线缺口

```sh
python3 -m unittest discover -s scripts/desktop-upstream -p 'test_*.py' -v
actionlint .github/workflows/desktop-upstream.yml
# 只读 API，需本地 gh 登录；不会发布任何内容：
python3 scripts/desktop-upstream/tracker.py observe --repo backspace135/chichu-toolbox --output /本地现有目录/observation.json
```

受控测试使用内存 GitHub API，覆盖首次/无变化、双基线、新 SHA 验证失效、调查不建候选、创建/复用 PR、冻结及下一批、Tag 对象/目标移动、Release 编辑/资源替换、API/分支失败保留成功值、历史改写、检查实际调用和指纹拒绝。没有真实调用写 API。

**尚未上线，不满足 #16 关闭条件**：未创建远端 state/候选分支或 PR，未触发 Actions，尚无真实运行链接、PR 写权限证明或 check run 证据。上线后需要在授权的 GitHub 环境依次取得首次/无变化/变化/同源复用证据（变化应使用受控测试来源或获授权的清单变更），确认候选 head 的显式 check run，核对失败与冻结记录，并补齐实际集成源的构建、双平台/真机验收。初始真实调查源不应为凑 PR 验收而伪装成已集成。此缺口不能用本地测试或只读观测替代。

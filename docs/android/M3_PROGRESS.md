# M3 开发与验证记录

任务：[M3 #3](https://github.com/backspace135/chichu-toolbox/issues/3)。协议、身份、签名与共存边界见 [M3 整合记录](M3_KERNELSU_INTEGRATION.md)。

## 交付（2026-11-09，JST）

- `:core:ksu`：KernelSU UAPI 6 JNI 桥（探测、安全模式、app_profile 读写）、ksud 仓库层（UAPI 6 固定 `730f6446` + UAPI 5 固定 v3.3.1 双通道）、严格 JSON 模块解析、策略模板与操作日志。
- 应用侧：设置内「Root 管理」弹层（状态 / 超级用户与策略 / 模块 / 维护 / 身份与迁移 / 日志）、模块 WebUI（按模块独立 HTTPS 虚拟源、WebMessage 桥、CSP 与目录逃逸拦截）。
- 签名与资源：v2-only 签名（禁 v1/v3/v4 与 dependenciesInfo）、`ksu_identity.py` 提取内核绑定参数、构建与 CI 校验 ksud 二进制摘要与 APK 入口。
- 测试：19 项 `:core:ksu` JVM 单测、3 项签名解析 Python 单测、一次性 `M3Instrumentation` 设备验收入口与本地模块夹具。

## 本地验证

```sh
# 工作目录 apps/android/
bash ./gradlew :app:assembleDebug :app:assembleRelease lint testDebugUnitTest --no-daemon
python3 scripts/test_ksu_identity.py
python3 scripts/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release.apk
```

全部通过；debug/release 均为 debug 签名的压缩测试包（v2-only，证书 744 字节 / `7ba84aa1…1418`，仅本机有效）。

## 真机证据

### cannon（M2007J22C，Android 12，未 Root）

- 内置 ksud 3.3.1-38-g730f6446 版本读取正常；JNI 探测 errno -19（无驱动通道），页面如实显示管理通道未就绪。
- `su` 被 SELinux 拒绝时展示真实错误，不显示假成功。
- 压缩 release 安装运行正常，管理操作禁用原因清晰。

### dada（24129PN74C，Android 16，KernelSU 32680 / UAPI 5 / 临时加载，原版 Manager v3.3.1）

- 原版 Manager 显示「工作中 [越狱模式]」，用户批准后仅对 `com.chichu.toolbox` 授予 Root；工具箱 `su -c id` 返回 UID 0。
- 已有 Root 通道验证：读取真实内核信息（version 32680、flags 0x5、UAPI 5），自动选用 UAPI 5 ksud；工具箱未取得管理器身份，超级用户策略入口保持禁用并说明原因。
- 通过工具箱同 UID 的 ksud 完整执行（夹具 `chichu_m3_acceptance`）：ZIP 安装 v1 → 禁用 → 启用 → 更新 v2 → action（返回 UID 0 与标记文件）→ 卸载标记 → 撤销卸载。ksud 5 将安装内容暂存为待更新，未为测试重启设备；action/WebUI 所需文件仅在模块目录内就绪。
- WebUI：页面加载，`ksu.exec` 经来源受限桥返回 `uid=0(root)`；`fetch` 外部 https 被阻止，webroot 外路径返回 404，`../module.prop` 由 realpath 拦截；CSP 与每模块独立 host 均生效。
- 与原版共存：原版安装、授权与管理关系全程未变，工具箱未静默接管。

设备证据保存在本地 `docs/assets/ui-validation-20261109/m3-*.png|xml` 与验收临时目录，不纳入 Git。

## 已知 UI 问题（转 M3.5）

- WebUI 确认框与结果弹层叠加时布局拥挤、标题遮挡按钮。
- WebView 自动请求 `favicon.ico` 失败时错误文案盖住页面内容；子资源 404 不应按整页失败展示。

## M3.5 界面修复（2026-11-10，JST）

关联任务：[#13](https://github.com/backspace135/chichu-toolbox/issues/13)。

- Root 状态、模块、策略、操作确认、WebUI 信任与页面共用一个详情弹层；确认与 WebUI 不再叠加底层执行结果。系统返回逐级回到模块列表、Root 状态、设置；关闭按钮及外部关闭结束整个 Root 弹层并清除本次 WebUI 信任。
- 详情标题与 48dp 关闭按钮分列，标题可换行；确认正文独立滚动，主操作和取消固定在底部。切换内容重置滚动位置，避免沿用上一页的滚动偏移。
- 仅 WebUI 主文档读取、网络与 HTTP 失败显示页面诊断；favicon 等子资源读取失败返回 HTTP 404，不占据正常页面。
- 授权列表未成功读取使用独立状态，模块列表读取成功不再产生「零授权」摘要；没有管理器身份时说明不可读取及原因。成功读取后分别统计允许的 UID 与应用数。
- 保留真实边界的文件读取 `IOException` 处理与主文档失败反馈；未加入重试或失败默认空列表。

### 构建与回归

- `ANDROID_HOME="$HOME/Library/Android/sdk" bash ./gradlew :app:assembleDebug :app:assembleRelease lint testDebugUnitTest --no-daemon` 通过；后续关闭行为修正后，重新执行受影响的 `:app:assembleDebug :app:assembleRelease :app:lintDebug :app:testDebugUnitTest` 通过。
- JVM 报告共 83 项，失败 / 错误 / 跳过均为 0；包含新增的「已有 Root 模块通道不显示零授权」「只有成功读取空列表才显示零授权」回归用例。
- app lint：0 错误、20 警告；debug/release APK 均通过 `scripts/verify-apk.py` 的 v2 签名、入口、四 ABI 与许可证检查。release 仍为本机 debug 签名的压缩测试包。

### dada 真机验收

- 设备：24129PN74C，Android 16；已有 Root 验证返回 UID 0、KernelSU 32680 / UAPI 5 / 临时加载。授权摘要明确显示未取得内核管理器身份、列表不可读取。
- 浅色 / 深色：检查 Root、模块、信任、WebUI；缺少 favicon 的本地夹具页面正常显示，无整页错误提示。模块自身 HTML 保持原样，其白色背景由模块定义。
- 系统返回：信任 / WebUI → 模块列表 → Root 状态 → 设置均可达；修复 miuix 返回动画先把弹层移出屏幕后内容不可见的问题。最终 debug APK 的 WebUI 关闭按钮已验证可直接回到设置。
- 主文档失败：临时将夹具 `index.html` 改名，显示真实本地 URL 与 HTTP 404 诊断；随后恢复文件。
- 200% 字体：原生 369×822dp 深色及通过 ADB 调整为 360×640dp 的浅色均检查；正文可滚动，信任与取消固定可达，标题和关闭按钮无重叠。
- 设备分辨率、密度、字体与应用主题均已恢复（主题为跟随系统）。本次仅安装/准备 `chichu_m3_acceptance` 本地夹具，没有重启；该夹具保留为待更新状态。
- 本次验证 WebUI 页面显示及加载错误路径，未重新确认页面按钮的 `ksu.exec` 回调结果；Root 桥此前 M3 的协议验收记录见上文。

本地截图与 UI XML：`docs/assets/ui-validation-20261110/m35-*.png|xml`（不纳入 Git），包括 `m35-trust-light`、`m35-trust-dark`、`m35-back-modules`、`m35-main-document-error`、`m35-trust-dark-font200`、`m35-trust-light-small-font200` 及正文滚动截图。

## M3 补充真机验收（2026-11-10 13:24–13:27，JST）

- 用户允许重启和安装测试 APK；本轮使用设备上已有的 `M3Instrumentation`，未安装新 APK、未重启或切换内核。
- dada（24129PN74C，Android 16）：已有 Root 返回 UID 0；KernelSU 32680、flags `0x5`、UAPI 5、late-load。原版 Manager 保留；工具箱界面明确显示未取得管理器身份、授权列表不可读取，没有误报零授权。
- 通过工具箱 UID 的仓库入口验证夹具模块禁用 / 启用、卸载标记 / 撤销卸载，均读回对应状态；安装 v2 返回成功，列表显示 `2.0`、`update=true`，只证明待更新状态，不代表重启应用完成。
- 实际 WebUI 页面按钮通过 WebMessage 桥回调返回 `ROOT_EXIT=0`、`uid=0(root)`、`WEBUI_OK`；页面越界请求返回 404，仓库越界读取返回错误 13，外部 HTTPS fetch 被阻止。
- **action 验收未通过**：当前生效目录缺少 `action.sh`，执行输出 `can't open '/data/adb/modules/chichu_m3_acceptance/action.sh'`，但 ksud 返回 0，现有 instrumentation 因只检查退出码仍报告成功。不能将该结果计为 action 成功；后续需验证模块更新生效后的标记文件，并处理退出码与实际执行结果不一致的反馈。
- 当前内核无 `/sys/module/kernelsu/parameters`，没有可用 debug 迁移入口；管理器策略写入读回、独立测试应用允许 / 拒绝 / 撤销及迁移矩阵仍受配套内核资源链阻塞。临时加载模式重启可能丢失当前 Root，本轮保留运行状态。
- 验收结束夹具保持启用、无卸载标记、v2 待更新；没有手工复制待更新文件冒充更新生效。

本地截图、UI XML 与设备信息：`docs/assets/ui-validation-20261110/m3-acceptance-root/`、`m3-acceptance-web/`、`m3-acceptance-web-exec/`（不纳入 Git）。

## cannon Magisk 环境补充验收（2026-11-10，JST）

- 设备 M2007J22C，Android 12，内核 `4.14.186-perf-00243-g9a0d96109bda`。ADB Shell 的 `su -c id` 返回 UID 0、`u:r:magisk:s0`，Magisk `9476e728-alpha`；未发现 KernelSU。Bootloader 报告 locked / green。
- 用户要求使用 Root 安装；通过 Root `pm install -r` 成功安装工具箱及测试 APK，临时安装包已清理。
- 工具箱 UID 的真实执行和页面「检查已有 Root」均返回 `Cannot run program "su": error=13, Permission denied`，没有获得可用 App Root 会话。页面保留错误及 KernelSU 驱动 errno -19，不显示已验证管理通道或零授权摘要。
- 当前 4.14 内核不能使用仅 android15-6.6 的配套 LKM；本轮没有安装模块、刷写、重启、修改 Magisk 授权数据库或系统访问控制。
- 证据：`docs/assets/ui-validation-20261110/m3-cannon-root-denied/`（截图、XML、JST 时间与结果，不纳入 Git）。本轮只完成拒绝路径验证，不替代 KernelSU 管理器验收。

## 尚未完成（不满足 #3 关闭条件）

2026-11-10 补充开发：已生成绑定本机工具箱测试签名的 android15-6.6 LKM 与配套 ksud（两 ABI），详见整合记录的「配套测试构建」。尚未加载或验证管理器身份，下列完整交付缺口仍保留。模块 action 改为匹配 BusyBox 环境执行并保留真实脚本退出码；新增缺失脚本、退出 37、成功环境传递三项 JVM 回归通过。安装到 dada 后缺失 action 返回退出码 2、instrumentation 返回失败，不再误判成功；验收入口同时要求 Root / 完成输出和模块内标记文件。尚未验收 action 成功与模块重启生效。

最终配套 APK 的 `:app:assembleDebug`、三项 `ModuleActionTest` 和 `scripts/verify-apk.py` 通过；已覆盖安装到 dada，已有 UAPI 5 通道与模块列表读取正常，缺失脚本再次明确返回失败。未执行全量测试、发布或 Git 提交。手机保持原版临时 Root；检测到 Bootloader locked / Verified Boot green，未尝试刷写或漏洞提权，完整 manager 验收仍未完成。

1. ~~工具箱签名绑定的内核/LKM 与配套用户空间资源链~~：2026-11-16 经 GhostLock 合法通道闭环（见下节），配套 ksud 内嵌工具箱绑定 LKM。
2. ~~管理器身份下的超级用户策略写入读回~~：2026-11-16 在 dada 以 MANAGER 身份完成允许/拒绝/撤销/重新请求/自定义 UID 全矩阵，内核读回一致。
3. 原版共存顺序矩阵、debug 内核迁移与迁移后身份验证（本机无 CONFIG_KSU_DEBUG 参数）。
4. M2 临时 Root → M3 完整闭环（依赖 M2 提权执行引擎）。
5. ksud 原版模板库、SELinux 规则编辑与 WebUI 完整协议（spawn、文件 IO、包管理 API）。

## M3 管理器身份与完整矩阵（2026-11-16，JST）

关联：#37 交接。本节全部经工具箱自身仓库层与真实页面完成，无手工内核策略伪造。时间均为 JST。

### GhostLock 合法整合通道（dada）

- GhostLock（com.ghostlock.app 1.2）不自带 LKM：设置里可选择任意已安装应用作为根管理器，运行时执行该应用 `libksud.so` 的 `late-load --kmi`，装载其构建时内嵌的 LKM；MANAGER 身份由 LKM 编译期绑定包名与签名证书（`KSU_PACKAGE_NAME`/`KSU_EXPECTED_SIZE`/`KSU_EXPECTED_HASH`）。
- 在 dada 上将 GhostLock 根管理器切至彳亍工具箱并重跑：工具箱内嵌 android15-6.6 LKM（绑定 `com.chichu.toolbox` 与本机调试证书，arm64 ksud SHA-256 `8d4668b4…a9122`）被加载。不刷写、不关闭签名校验、不伪造 MANAGER 标志。内核上报 KernelSU 32718 / UAPI 6 / 临时加载；ksud Root 通道验证返回 UID 0。
- 原版 Manager 与工具箱全程共存（原版显示“未安装”状态，身份未静默接管；用户随后自行卸载原版，卸载前已截图取证）。设备重启后临时 Root 丢失属预期，重跑 GhostLock 恢复；Shizuku 需先经 `adb shell <shizuku libpath>` 重启。

### 独立 App 授权矩阵

验收客户端新增 `RootProbeActivity`（每次启动发起一次真实 `su -c id` 并回报退出码/输出）；工具箱声明 `QUERY_ALL_PACKAGES`（管理器必须枚举全部应用）。

| 步骤 | 内核策略 | 探针结果 |
| --- | --- | --- |
| 默认（无策略） | 不允许 | `ROOT_EXIT=-1 error=13 Permission denied` |
| 允许 Root（内核默认策略） | 允许 | `ROOT_EXIT=0 uid=0(root) … context=u:r:ksu:s0` |
| 撤销 Root | 恢复默认不允许 | `ROOT_EXIT=-1 error=13` |
| 撤销后重新请求 | 仍不允许 | `ROOT_EXIT=-1 error=13` |
| 重新允许 | 再次允许 | `ROOT_EXIT=0 uid=0(root)` |
| 自定义策略目标 UID=2000 | uid=2000 | `ROOT_EXIT=0 uid=2000(shell) gid=0(root) context=u:r:ksu:s0` |

- 所有策略写入均通过内核读回校验（页面提示“策略已写入并通过内核读回验证”）；自定义 UID 字段在真实 Root 会话生效。
- 全程 `adb shell su -c id` 独立可用（Shell 与 Root 授权相互独立）。

### 模块 v3 跨重启生命周期

- 夹具新增 `service.sh` / `post-fs-data.sh` 标记脚本（打包新增 v3；v1/v2 保持无启动脚本）；M3Instrumentation 入口放行 v3。安装后暂存（update=true）。
- 重启并经 GhostLock 重载后：生效目录出现 `webroot`/`service.sh`/`boot-count`/`service-ran.txt`，update 标志消失；`boot-count=1`，`service-ran.txt` 记录本次启动以 `uid=0(root) context=u:r:ksu:s0` 执行——模块跨重启真实生效且有可观察效果。`post-fs-data-ran.txt` 不存在是 late-load 模式的正确行为（该阶段早于 LKM 装载）。
- 禁用模块后重启并重载：标记文件不再增长（禁用跨重启真实生效）。

### WebUI 补验收（在真实生效的模块上）

- `ksu.exec` 桥：`ROOT_EXIT=0`、`uid=0(root)`、`context=u:r:ksu:s0`、`WEBUI_OK`。
- 目录逃逸：`ESCAPE_STATUS=404`（`/%2e%2e/../module.prop` 被拦截）。
- 关闭后行为：关闭 WebUI 再打开再次出现信任确认；取消不打开页面。

证据（本地，不纳入 Git）：`docs/assets/ui-validation-20261110/m3-manager-verified.png`、`m3-original-manager-coexist.png`、`m3-rootprobe-denied(-fresh).png`、`m3-rootprobe-allowed.png`、`m3-rootprobe-revoked.png`、`m3-rootprobe-custom-uid2000.png`、`m3-module-v3-activated.png`、`m3-webui-exec-after-reboot.png`、`m3-webui-trust-reappear.png`。

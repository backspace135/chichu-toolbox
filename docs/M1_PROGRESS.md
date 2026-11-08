# M1 实施记录

计划：`docs/superpowers/plans/2026-11-08-m1-implementation.md`。

## 基线

- 2026-11-08：基于 `b734cd5`，原仓库只有文档与被忽略的上游源码，无构建工程，无现有测试。
- 工作分支：`feat/m1-permission-service`。保留开始前未跟踪的 UI 规范和实施计划，不提交或推送。
- 决定：在原目录的独立功能分支实施，不另建 worktree；本地 upstream 调研目录保持不变，构建产物不得依赖它。
- JDK 21 和 Android SDK 已安装，SDK 包目录为 `platforms/android-37.0`。Gradle 使用 `D:/tools/caches/gradle` 缓存。

## 接口预检

| 任务关系 | 检查结果 |
| --- | --- |
| 1 → 2–6 | 五模块 + catalog 提供统一构建，AGP 内置 Kotlin，避免重复应用 Kotlin Android 插件 |
| 2 → 4、5、6 | ServiceState 和 ShellGrant 为领域模型；Root 独立，UI 不依赖 Android Binder 类型 |
| 3 → 4、5 | 类编入主 APK，native starter 启动；不使用旧 start.sh/dex 架构 |
| 4、5 → 6 | 状态流和操作接口由 repository 暴露，页面不直接操作授权文件 |
| 3–6 → 7 | 真机证据与 JVM 测试分离，未测场景标记未验证 |

## 任务状态

- 任务 1：进行中，五模块配置和依赖解析通过，待完成完整 APK／lint／干净目录验收。
- 任务 2：状态／授权／启动命令模型已有 11 个 JVM 测试通过记录；整体集成后仍需回归。
- 任务 3：服务端和 native 已移植，Java 编译与四 ABI native 构建通过；真实 Binder／第三方 SDK 兼容性待设备验收。
- 任务 4：ADB 配对、连接、NSD、本机手动端口和当前安装路径命令已实现；取消和旧任务资源隔离测试通过，无线配对待设备验收。
- 任务 5：自有管理器连接、授权仓库、一次性 token 查询／同步确认已实现；安全模型和死亡测试通过，跨 UID 集成待设备验收。
- 任务 6：Application／AppContainer／ViewModel／三页 miuix 界面已编写，首轮编译、测试和 lint 进行中；尚无视觉验收结论。
- 任务 7：未完成。已发现一台 ADB 在线设备，未安装 APK 或启动服务。

## 构建与测试过程

- Gradle 9.7.1 下载及 SHA-256 校验完成，AGP 和五模块配置成功进入编译。
- 首次构建依赖下载曾超时，重试后已通过解析；线程快照显示 HTTP/TLS 等待而非死锁。
- 状态测试最初因类型未定义而编译失败，补实现后通过；ActivationCommand 未单独观察 RED，不将这些记录描述为全部严格断言 RED→GREEN。
- 服务移植后 Java 的 42 个错误来自缺少 AndroidX annotation：显式引入 `annotation:1.9.1` 后编译通过。
- native 编译命令同时使用 prefab libcxx 头文件与 NDK 默认 STL，破坏 `include_next` 链路；对照上游恢复 `-DANDROID_STL=none`，四种 ABI 的 `externalNativeBuildDebug` 均通过。未修改标准库头文件或隐藏错误。
- `PendingPermissionsTest` 首轮受上述 Java 错误阻塞；修复后确认类型缺失，再以空方法骨架实际运行 4 个测试，3 个因未实现失败、未知 token 用例通过。已实现存储，整套测试和 APK 构建待结果。
- 后续整套 JVM 测试和无界面 debug APK 已两次通过；Shell 通道双流、退出码、输出限额和超时销毁测试通过。
- ADB／ActivationGate 首轮整套测试与 APK 打包通过，但 lint 报 13 个 RestrictedApi 错误；均为内置协议实现使用官方受限内部 API，在具体协议类说明原因并局部豁免，没有全局关闭 lint。随后 `testDebugUnitTest lint` 通过（日志 `chichu-gate-lint-green.log`），仍存在上游 hidden API、ADB trust manager 等警告，不能表述为无警告。
- 发现 Controller 的全局 activeClient 会被旧 finally 清空，改为按 operation 注册传输中断；取消仅关闭 socket，native pairing 上下文由执行线程释放，避免并发销毁。增加两个资源隔离测试，先观察缺失接口编译失败，再通过整套测试。45 秒 watchdog 独立于阻塞 IO 执行，协程超时不再单独承担打断 socket 的职责。
- UI 纯模型测试先观察 presentation／搜索模型缺失失败，当前进行首次 GREEN 验证。Compose 交互、无障碍、系统栏与键盘行为尚未设备验证。
- Ruling: 保留已获批准的 miuix 视觉与 Compose Navigation，不因 Better Design MCP 未连接而重做方案；未取得该 MCP 指导或审查结果。
- Ruling: 修正旧文档的 start.sh／独立 dex 和「后推 Binder 覆盖」错误，当前实现与固定上游源码事实优先；若设备行为不同，应补验收记录而非隐藏差异。
- 下一步：完成界面编译与许可证内置，检查 APK 入口和 native，再经用户确认执行设备安装／激活；APK 构建通过不代表真机验收完成。

## 2026-11-08 收尾进展（以下更新优先于上方阶段快照）

- 最新完整 `testDebugUnitTest :app:assembleDebug lint :app:assembleRelease` 成功，45 个 JVM 测试无失败／错误／跳过；release 为 unsigned。新图标替换后日志 `chichu-pr-final.log`，此前审查修复日志 `chichu-review-final.log`。
- 不含 ignored upstream 的独立源码副本完成四 ABI native 与 debug 打包，验证当时工程不依赖本地调研目录；该副本早于最终审查修复，未用最新版重测。
- 图标已由早期字形渐变版改为用户确认的纯蓝白几何工具箱，同步 adaptive／monochrome／SVG；预览含桌面小尺寸与单色版，使用本机 Edge 渲染，不冒称 Better Design MCP 审查。
- 用户已明确批准真机安装和 Shell 激活；Android 12 真机已验证官方 SDK attach、拒绝／批准／撤销、Shell UID 2000、UserService UID 2000 与销毁、非管理事务拒绝、伪造 token、服务死亡后离线与重新激活。
- UserService 实测暴露 MediaTek 空进程名 NPE，按 app_process 缺少绑定数据修复；真实失败→成功证据和限制见验证结果。
- 浅色稳定截图已检查，背景改用 miuix surface，与白色 Card 形成分组对比。
- 新增默认只读的 `scripts/verify-m1.ps1`；显式 `-InstallDebug`／`-Activate` 才改变设备；不操作原版和其他应用授权。
- Ruling: MediaTek 兼容修复保留在服务辅助进程，不要求第三方更改 SDK；代价是新增 hidden API 绑定数据依赖，需要跨版本设备回归。
- 当前仍不标记 M1 全部完成：无线端到端、共存矩阵、完整 release 安装运行和完整视觉矩阵未完成。压缩 release DEX 已取得部分设备证据；用户已选择配合无线验收，等待新的即时配对参数。
- 详细证据：[M1_VALIDATION_RESULTS.md](M1_VALIDATION_RESULTS.md)；验收矩阵：[M1_ACCEPTANCE.md](M1_ACCEPTANCE.md)。

## 最终审查修复与验证边界

- C1：APK 类检查实测 release 缺失 `RishTerminal`，会阻断 JNI 初始化；两个 rish JNI 类显式 keep 后 debug／release 检查 GREEN，CI 已加入检查。压缩服务 DEX 真机 attach、Shell 和 UserService 通过；不是完整 release 安装验收。
- I1：授权读取增加当前包身份验证，UID 被回收且原包不再存在时移除旧批准；模型测试 GREEN，未在设备实际复现 UID 回收。
- I2：撤销按当前安装包与客户端包的并集清理，不依赖活客户端；排队记录删除后不再启动，销毁清除定时器；模型及 daemon 真机撤销 GREEN，未穷举启动竞态。
- I3：候选 token 验证成功后才替换弹窗；完成操作按 token 清理，旧确认不能清空新请求；模型 GREEN，真实弹窗期间发送伪造 token 的真机回归通过，双真实请求异步交错仅有模型覆盖。
- I4：两类 NSD 共享串行解析队列、有限重试及服务代际；并行发现／旧结果／重试上限模型 GREEN，自动发现设备端到端仍未验证。
- 部分新增测试先观察缺失接口编译失败，不将其描述为全部严格断言 RED→GREEN。
- Final: minor (deferred): 无线激活成功后服务死亡可能保留旧激活成功文案；主服务状态已离线且执行受保护，后续做独立文案状态回归。
- Final: Ruling: NSD 迟到回调问题与 I4 使用同一队列代际修复，不另开扩展功能 — 避免新解析队列保留已失效服务 — 若代际失效将显示过期端口，手动输入仍可用。
- MediaTek 审查时尚无回归证据，但随后本会话已取得同一设备 `USER_SERVICE_UID=2000` 和销毁证据；审查中的该验证缺口已由新证据覆盖，其他 OEM 仍未验证。
- 无线尝试当前端口返回 ECONNREFUSED，未配对成功；不记录配对码，等待新的系统弹窗端口。

## 用户确认的交付安排

- 用户确认几何工具箱图标并要求替换、创建 PR、合并及关闭 #2；此前不提交／不推送的限制由本次明确授权取代。
- 按用户要求交付现有 M1 基础实现，但无线端到端、原版共存矩阵、完整 release 安装及其余视觉矩阵仍未验收，不把关闭 issue 表述为这些项目已通过。
- PR 与关闭说明须链接验证记录，保留未覆盖项及低优先级成功文案残留问题；不为合并擅自改变设备系统设置或操作原版。

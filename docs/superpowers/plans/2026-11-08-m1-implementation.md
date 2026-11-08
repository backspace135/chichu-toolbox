# M1 工程骨架与内置权限服务实施计划

> **执行者要求：** 使用 superpowers:executing-plans 在当前会话逐项实施；每项完成后记录验证证据。未获用户要求不提交、不推送、不关闭 issue。

**目标：** 交付无需安装原版 Shizuku 即可通过无线调试或电脑 ADB 激活的工具箱 APK，管理兼容官方 SDK 的第三方 Shell 授权。

**架构：** 五个业务模块，单 Activity、Compose Navigation、手写 AppContainer。公开客户端 API 使用官方 Maven 依赖；未发布的服务端共享实现和 native 启动组件按固定提交移植到 `:core:shizuku`，不依赖被忽略的本地 upstream 目录才能构建。

**技术栈：** AGP 9.4.1、Kotlin 2.4.20、Gradle 9.7.1、JDK 21、Compose BOM 2026.09.00、miuix 0.9.4、Shizuku API/provider 13.1.5。

**规格：** [技术栈](../../TECH_STACK.md)、[Shizuku 设计](../../M1_SHIZUKU_DESIGN.md)、[UI 设计](../../M1_UI_DESIGN.md)、[issue #2](https://github.com/backspace135/chichu-toolbox/issues/2)。

## 全局约束

- `applicationId = com.chichu.toolbox`，`minSdk = 31`；compileSdk/targetSdk 37，基于当前 KSU 快照验证 SDK 可用性。
- 模块固定为 `:app`、`:core:common`、`:core:shizuku`、`:core:ghostlock`、`:core:ksu`；后两者仅建立边界，不实施 M2/M3。
- 自有 `com.chichu.toolbox.permission.MANAGER`，官方 API 权限名及 Binder 协议保持兼容。
- 不自动停止、替换或接管原版，不沿用会查杀原版进程的 starter 逻辑。
- Shell 授权独立于未来 Root 授权；未授权客户端不能调用高权限或管理接口。
- 上游代码保留许可证及来源；新增注释、文档与用户文案用简体中文。
- 版本不存在或依赖不可取得时记录阻塞，不静默降级、不伪造构建成功。

## 重点审查

1. 原版与工具箱共用 API 权限，可能共享系统运行时授权：不能因此自动获得内置服务批准；任务 3 和 7 验证。
2. native starter 的旧进程查杀、配置文件路径仍可能跨实例影响：必须隔离进程名和授权文件；任务 3 和 7 验证。
3. Manager 特殊事务、UserService 中转、权限结果入口不能仅靠 UI 隐藏保护；任务 3、4 和 7 验证。
4. APK 升级改变 native 路径，配对与连接端口不同，断线期间操作不能误报成功；任务 2、5、6 验证。
5. 官方 Provider 已有存活 Binder 时忽略新 Binder，不能假设后启动服务覆盖连接；任务 7 记录多种顺序。

---

## 任务 1：建立可重复构建的工程与最小 CI

**文件：** 新建根 `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、Gradle wrapper、`gradle/gradle-daemon-jvm.properties`；各模块 `build.gradle.kts` 与必要 Manifest；`.github/workflows/android.yml`、`README.md`；修改 `.gitignore`。

**接口：** 后续任务使用五个模块及 catalog 中固定的依赖，公开 API 不使用 upstream project 引用。

- [ ] 核验仓库状态、本地 SDK/NDK/CMake/JDK 和固定构建版本的实际可取得性。
- [ ] 建立五模块工程，固定 Gradle distribution 校验值；只启用本任务需要的插件，保留后续 protobuf/apksign 的版本记录而非无用途应用。
- [ ] 建立 `:core:common` JVM 单测入口，运行一个基础测试验证测试发现机制。
- [ ] 配置 CI：checkout、Temurin 21、Android SDK、Gradle 缓存、`:app:assembleDebug lint testDebugUnitTest`、APK 上传。
- [ ] 从干净目录验证工程不依赖 `third_party/upstream`；记录本地构建命令与结果。

## 任务 2：先实现可测试的状态与授权领域模型

**文件：** `core/common/src/main/kotlin/com/chichu/toolbox/core/common/ServiceState.kt`、`ShellAuthorization.kt`、对应 `src/test`；`core/shizuku/.../activation/ActivationCommand.kt` 与对应测试。

**接口：** `ServiceState` 表达离线、激活中、已连接、失败；`ShellGrant` 表达询问、批准、拒绝；Root 不复用这些类型。`ActivationCommand.create(nativeLibraryDir: String, apkPath: String): String` 生成正确 shell 引号的命令。

- [ ] 先写 `ServiceStateTest`：死亡使连接状态离线、旧异步操作结果不能覆盖较新状态。
- [ ] 先写 `ShellAuthorizationTest`：缺省不允许、拒绝和撤销均不能执行；没有从 Shell 到 Root 的隐式转换。
- [ ] 先写 `ActivationCommandTest`：包含空格和单引号的路径可正确引用；输入路径更新后命令更新。
- [ ] 运行测试确认失败，再实现模型和纯函数；再次运行确认通过。

## 任务 3：移植服务端、启动组件与权限隔离

**文件：** `core/shizuku/src/main/java/rikka/shizuku/server/`、服务端共享代码、`moe/shizuku/starter/`、`rikka/rish/`、必要 common 工具类、`core/shizuku/src/main/cpp/`、模块构建脚本、`app/proguard-rules.pro`、`third_party/M1_UPSTREAM.md`、许可证文件。

**接口：** `rikka.shizuku.server.ShizukuService.main(String[])` 是独立 app_process 入口；`moe.shizuku.starter.ServiceStarter` 保持 UserService 启动兼容。服务类通过 library 依赖编入主 APK，native starter 以 APK 为 CLASSPATH。

- [ ] 固定 Shizuku `b844bc49` 和 Shizuku-API `a27f6e4` 的完整 SHA 与许可证，记录具体移植范围及改动。
- [ ] 保留公开 Maven `api/provider:13.1.5`；仅移植未发布的 server-shared/rish 及服务端、启动器所需代码，消除类重复。
- [ ] 隔离 manager 包名、自有 MANAGER 权限、provider authority、服务进程名 `chichu_shizuku_server`、配置文件 `chichu-toolbox-shizuku.json`、native starter 包名与查杀目标。
- [ ] 核验所有身份入口：attachApplication、特权调用、管理事务 10001、授权结果、flags 管理、exit、UserService 附着和中转；不因修改常量就假定完成。
- [ ] 隔离内置批准状态与共享 API_V23 系统权限：不从原版的系统运行时授权自动导入批准，不以其绕过内置批准，且不静默改动原版权限状态。
- [ ] 保留官方协议 descriptor、事务码、BinderContainer key 和 SDK 握手，不能全局替换 `moe.shizuku.*` 字符串。
- [ ] 核验 AGP 9 下 refine/native 打包；保留 extracted native starter，配置所有反射入口 keep 规则。
- [ ] `:app:assembleDebug` 后检查 APK 含服务类及 starter/rish native 产物，adb 启动后检查 UID=2000、进程名及 Binder 送达。

## 任务 4：管理器连接、第三方授权与内部 Shell 通道

**文件：** `core/shizuku/.../ToolboxManagerProvider.kt`、`ShizukuRepository.kt`、`AuthorizationRepository.kt`、`ShellChannel.kt`；`app/.../AppContainer.kt`、`ToolboxApplication.kt`、Manifest。

**接口：** repository 暴露 `StateFlow<ServiceState>`、应用授权列表，以及批准/拒绝/撤销方法。`ShellChannel.execute(command: List<String>): ShellResult` 为内部受控调用接口，返回退出码与输出；UI 不直接操作全局 Shizuku 状态。

- [ ] 编写可隔离状态适配器的失败测试：Binder 死亡清除在线状态、远端异常不显示操作成功、超时后旧结果不能复活连接。
- [ ] 自有 provider 仅接收可信 shell 服务请求，关闭自动 Sui 初始化；验证内置服务身份，避免 UI 连接错服务。
- [ ] 完成授权列表、权限结果回传和撤销，所有敏感操作由服务端再次校验；真实应用身份不信任外部 Intent 参数。
- [ ] 在单 Activity 内承接服务端授权请求；校验请求来源与当前待处理请求，返回/关闭按拒绝处理，不使用可伪造的 Intent 直接批准。
- [ ] 实现有超时、取消、输出上限的内部 Shell 调用通道，不暴露未鉴权的 exported 执行组件。
- [ ] 运行 JVM 测试和 debug 构建；真机检查管理器可连接并执行无副作用的 `id`。

## 任务 5：无线调试发现、配对与激活

**文件：** `core/shizuku/.../adb/`、`activation/ActivationController.kt`，native 配对组件与依赖 catalog；相关 Manifest 声明。

**接口：** `ActivationController` 暴露发现、配对、连接、启动、等待 Binder 的阶段状态，并支持取消与明确错误。

- [ ] 先测试端口范围、六位码校验、重复启动抑制、取消与超时状态；确认测试先失败。
- [ ] 按固定上游移植 ADB 协议、TLS/RSA 密钥及配对 native 部分，密钥写入应用私有存储，配对码不持久化、不写日志。
- [ ] NsdManager 分别发现 pairing/connect 服务，提供手动端口路径；配对成功不等于激活成功。
- [ ] 连接后执行当前 starter 命令，以经过验证的内置 Binder 到达为成功条件。
- [ ] 在需要时申请通知等平台权限，拒绝后提供说明和可用的手动前台流程。
- [ ] 运行 JVM 测试、构建、lint；真机从未配对状态完成配对与激活并记录日志。

## 任务 6：用 miuix 落实统一界面

**文件：** `app/.../MainActivity.kt`、`ui/ToolboxApp.kt`、`ui/ToolboxViewModel.kt`、`ui/theme/ToolboxTheme.kt`、`ui/navigation/ToolboxNavigation.kt`、`ui/service/`、`ui/authorization/`、`ui/settings/`、`app/src/main/res/values/strings.xml`。

**接口：** ViewModel 通过 AppContainer 获取任务 4、5 的接口；页面只接收 UI 状态和事件，不内嵌高权限逻辑。

- [ ] 先测试 UI 状态映射：离线不是空列表、失败不是成功、Shell 不是 Root；测试先失败后实现。
- [ ] 按 M1_UI_DESIGN 实现主题与三个入口；600dp 切换侧栏，内容限宽 800dp，保持 Compose Navigation。
- [ ] 实现真实服务状态卡、按状态变化的主操作、无线调试分步、当前电脑命令复制、执行日志弹层。
- [ ] 实现授权搜索列表、真实确认界面及离线/错误/无结果状态；不显示 M2/M3 假开关。
- [ ] 实现外观与关于页、依赖许可证；共存提示区分安装、运行和未知。
- [ ] 验证大字体、深浅色、键盘与系统栏、窄屏与宽屏、无障碍按钮描述；保存实际截图而非仅以源码推断外观。

## 任务 7：验收、共存矩阵与交付

**文件：** `docs/M1_ACCEPTANCE.md`、`docs/M1_VALIDATION_RESULTS.md`、`scripts/verify-m1.ps1`，修正 `docs/M1_SHIZUKU_DESIGN.md` 与 README。

**接口：** 验收脚本接受 adb 路径、设备 serial，默认只读检测；安装、停止或授权变更需明确指定，不自动清除原版数据。

- [ ] 运行 `gradlew.bat :app:assembleDebug lint testDebugUnitTest --no-daemon`，保存真实结果与 APK 位置。
- [ ] 不安装原版时分别执行电脑 ADB 与无线调试激活；检查第三方未修改 SDK 客户端连接、批准后调用、拒绝和撤销后不可调用。
- [ ] 使用测试客户端检查管理事务、权限结果伪造、UserService 附着和未经授权的高权限调用均被拒绝。
- [ ] 停止内置服务、断开连接、重启设备后检查 UI，不保留假在线状态。
- [ ] 在用户确认的真机上执行两种安装顺序和两种服务启动顺序；记录同名权限安装结果、客户端连接归属、重启客户端/服务后的变化，不自动卸载用户已有 App。
- [ ] 检查启动内置服务不查杀原版、不读取原版配置、不导入原版授权；检测证据不足时 UI 显示未知。
- [ ] 更新设计中与源码或实测不符的假设；无设备或场景未执行时标记「未验证」，不关闭 issue #2。
- [ ] 输出阶段交付清单、命令、测试结果及剩余项；用户确认后再提交、推送或关联 issue。

# 技术栈决定（v0.1）

> 2026-11-08 讨论定案，适用于 [issue #1](https://github.com/backspace135/chichu-toolbox/issues/1) 第一阶段全部里程碑（M1–M4）。

Android 工程位于 `apps/android/`；本文中的模块与工程相对路径以该目录为基准。

## 结论总览

| 层面 | 决定 |
| --- | --- |
| 语言 / UI 框架 | Kotlin + Jetpack Compose |
| 组件库 | Material 3（底层规范）+ miuix（主要组件来源） |
| 导航 | 单 Activity + Compose Navigation |
| 模块化 | `:app` + `:core:shizuku` / `:core:ghostlock` / `:core:ksu` / `:core:common` |
| Shizuku API | Maven 依赖 `dev.rikka.shizuku:api`，不源码整合 |
| 依赖注入 | 无框架，手写 `AppContainer` |
| minSdk | 31（Android 12） |
| 构建 | AGP 9.4.1 + Kotlin 2.4.20 + Compose BOM 2026.09.00 + Gradle version catalog |
| 唯一 View 例外 | 模块 WebUI 用 WebView（ksu-webui 协议） |
| applicationId | `com.chichu.toolbox` |
| 测试 | 分层：纯逻辑 JVM 单测 + 真机脚本化验收 |
| CI | GitHub Actions 最小集：assemble + lint + JVM 单测 |

## 各项决定与依据

### 1. UI 框架：Kotlin + Jetpack Compose

三个整合对象中，KernelSU Manager 与 GhostLock App 已经是 Compose，UI 代码可直接搬运；Shizuku Manager 是传统 View/XML，但 UI 极薄（约十来个 layout，配对对话框、状态页、应用列表），重写成 Compose 成本低，其核心价值在 `server` 端，与 UI 框架无关。单 APK 约束直接排除 Flutter / React Native（自建渲染引擎，整合上游纯 Kotlin 代码需跨平台通道桥接）。

### 2. 组件库：Material 3 + miuix

- **Material 3** 是底层规范：主题、动态取色、深浅色模式、无障碍。
- **miuix**（`top.yukonga.miuix.kmp`，0.9.4）是主要组件来源。核查确认 KernelSU Manager 与 GhostLock 实际都使用 miuix（HyperOS 风格，构建在 M3 之上），跟随即可零改造搬运两家页面，三个模块视觉天然统一。目标设备为小米 HyperOS 机型，风格契合用户群。
- KernelSU Manager 已启用 Material 3 Expressive（`ExperimentalMaterial3ExpressiveApi`），整合其代码时跟进。
- Shizuku UI 重写时同样使用 miuix 组件。

### 3. 导航：单 Activity + Compose Navigation

KernelSU Manager 同款方案。三个模块（权限服务 / 临时 Root / Root 管理）各自一个导航图（navigation graph），共享的设备状态与任务流程集中管理。相比保留 Shizuku 的多 Activity，三合一后页面跳转与状态共享更干净。

### 4. 模块化：五模块

```
:app                # 单 Activity + Compose UI + 导航
:core:shizuku       # Shizuku 服务端整合（原 server / shell / starter / common）
:core:ghostlock     # 临时 Root 引擎（含原 :profile-core）
:core:ksu           # ksud 通信、Root 管理
:core:common        # 共享设备状态、任务流程、设置存储
```

三个上游各占一个 core，依赖版本冲突（libsu / okhttp 等）在各自模块内部消化，不互相污染；边界清晰是共存测试（验收标准第 5 条）的前提。v0.1 不再细拆 feature 层，避免过度设计。

### 5. Shizuku API：Maven 依赖，不源码整合

第三方 App 通过官方 SDK（`dev.rikka.shizuku:api`，GhostLock 使用 13.1.5+）连接内置服务。我们同样以 maven 依赖引入 binder 定义与常量，仅源码整合 server 端。这保证了「第三方 Shizuku 客户端能发现并连接内置服务」（验收标准第 2 条）在协议层面的正确性。

### 6. 依赖注入：无框架

三家上游都不使用 DI 框架，跟随此风格使搬运代码零改造。手写 `AppContainer`（顶层依赖容器 object）+ ViewModel Factory 满足工具类 App 需求，不引入注解处理器负担。

### 7. minSdk = 31（Android 12）

- KernelSU Manager 与 GhostLock 的 minSdk 均为 31，跟随即与两家生态现实一致。
- 无线调试配对（M1 核心流程）要求 API 30+，31 起步。
- 砍掉 Shizuku 面向 Android 7–11 的大量兼容分支；Shizuku 的 minSdk 24 是其作为公共 SDK 的普适性需要，本项目不需要承担。
- 临时 Root 依赖较新内核（6.6+），对应设备现实上均 ≥ 31。

### 8. 构建基准：对齐 KernelSU Manager 最新

- AGP 9.4.1 + Kotlin 2.4.20 + Compose BOM 2026.09.00，以本地快照中 KernelSU Manager（tiann/KernelSU @ 730f6446，2026-11-07）为准。
- 统一 Gradle version catalog（`libs.versions.toml`）管理依赖；GhostLock 硬编码在 `build.gradle.kts` 中的依赖在搬运时录入 catalog。
- Shizuku（AGP 8.10.1 / Kotlin 2.1.21）代码升级到 Kotlin 2.4 重编译。
- 保留 KernelSU Manager 的构建组件：**protobuf**（与内核侧通信）、**lsplugin-apksign**（管理器签名校验，Root 接管与共存迁移时使用）。

### 9. 例外：模块 WebUI

KernelSU 模块的 WebUI 走 ksu-webui 协议，必须由 WebView 承载。用 Compose `AndroidView` 包裹 WebView，这是全 App 唯一保留的 View 组件。

### 10. applicationId：`com.chichu.toolbox`

与三个上游均不冲突（避开 `moe.shizuku.*`、KernelSU Manager 包名）。所有派生物跟随此包名：自有权限名、`/sdcard/Android/data/<pkg>/start.sh` 路径、server dex 打包位置。与原版 Shizuku / KernelSU Manager 共存的前提就是包名互不相同。

### 11. 测试策略：分层，验收标准可重复执行

三个 core 模块可测性不同，按性质分层，不追求覆盖率：

- **JVM 单测（纯逻辑）**：`:core:ghostlock` 的 profile 匹配、内核版本检测、镜像 / OTA 配置提取（上游 `:profile-core` 本就是独立库，最适合）；`:core:common` 的设备状态与任务流程状态机；`:core:ksu` 的 ksud 响应解析（mock libsu shell I/O）、apksig 校验。
- **真机验收脚本化**：在 Xiaomi 15 上把 issue #1 验收标准变成可重复的 adb 命令序列 checklist（激活 → 第三方 App 连接 → 授权 / 撤销 → 重启后状态重检），每个里程碑执行并记录结果。
- **`:core:shizuku` server 端不写 JVM 单测**：重度依赖 hidden API 与 `app_process` 环境，JVM 无法承载；以上游同样做法（真机验证）为准。
- **不搞 instrumented 大盘**：核心场景（adb 激活、双 server、Root）模拟器无法复现；仅保留上游已有的 androidTest（KernelSU Manager 有）。

### 12. CI：GitHub Actions 最小集

单 job：`ubuntu-latest`，JDK 对齐 `gradle-daemon-jvm.properties` 工具链，执行 `gradle assemble lint test`；`gradle/actions/setup-gradle` 提供 Gradle 缓存（依赖树较大，必须有）。真机场景不进 CI。CI 构建用 debug keystore；发布签名密钥 M4 阶段确定——注意 apksign 校验与 KernelSU 管理器身份绑定，更换签名密钥时已发布版本的接管逻辑需向后兼容。

## 上游技术栈核查记录

决策依据的本地核查结果（快照见 [third_party/SOURCE_SNAPSHOT.md](../../third_party/SOURCE_SNAPSHOT.md)）：

| 上游 | 模块结构 | UI 技术栈 | DI | minSdk | 构建版本 |
| --- | --- | --- | --- | --- | --- |
| Shizuku | api / common / manager / server / shell / starter | Kotlin + View/XML | 无 | 24 | AGP 8.10.1 / Kotlin 2.1.21 |
| GhostLock App | `:app` + `:profile-core` | Kotlin + Compose + miuix | 无 | 31 | Kotlin 2.4.20，依赖硬编码 |
| KernelSU Manager | 单 `:app` | Kotlin + Compose + miuix + M3 Expressive | 无 | 31 | AGP 9.4.1 / Kotlin 2.4.20 / Compose BOM 2026.09.00 |

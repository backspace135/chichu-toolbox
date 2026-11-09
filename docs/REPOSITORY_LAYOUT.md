# 仓库布局

本仓库按客户端划分工程，Android、Windows 和 macOS 共用产品仓库，各自拥有独立的构建入口。

## 目录职责

| 目录 | 内容 |
| --- | --- |
| `apps/android/` | Android Gradle 工程；含 `app/`、`core/`、wrapper、`scripts/` 和 `testing/` |
| `apps/windows/` | Windows 客户端，待开发 |
| `apps/macos/` | macOS 客户端，待开发 |
| `docs/` | 产品与跨平台约定 |
| `docs/android/` | Android 专用技术栈、设计、实施计划、验收与验证记录 |
| `docs/assets/` | 产品图标和设计资源 |
| `third_party/` | 全仓库上游来源、固定版本记录和许可证 |
| `.github/workflows/` | 各平台工作流；工作目录与产物路径显式指定客户端目录 |

Windows、macOS 进入实施后，其平台文档分别放入 `docs/windows/`、`docs/macos/`。

## 构建与共享边界

- 从仓库根目录运行 Android wrapper 时，指定 `-p apps/android`；在 `apps/android/` 内可直接运行 wrapper。
- Android Studio 打开 `apps/android/`；本地 SDK 配置放在该目录的 `local.properties`，不提交。
- 各客户端的专用脚本、测试、构建配置与产物保留在各自目录中。
- `apps/android/core/` 是 Android 业务模块，只在该 Gradle 工程内共享。
- 待实际出现跨端共享实现时再建立对应共享目录；目前统一复用的是 `docs/assets/` 与 `third_party/` 中的资料。
- 平台迁移不改变 Android 的 applicationId、Gradle 模块名或输出文件名。

## 2026-11-09 目录迁移

| 原路径 | 当前路径 |
| --- | --- |
| `app/`、`core/` | `apps/android/app/`、`apps/android/core/` |
| `gradle/`、`gradlew`、`gradlew.bat` | `apps/android/` 下的同名路径 |
| 根目录 Gradle 构建配置 | `apps/android/` 下的同名配置 |
| `scripts/`、`testing/` | `apps/android/scripts/`、`apps/android/testing/` |
| `docs/M1_*.md`、`docs/TECH_STACK.md` | `docs/android/` 下的同名文件 |
| `docs/superpowers/` | `docs/android/superpowers/` |

M1 实施与验证记录中保留的历史本机日志、分支、提交与设备证据属于当时的结果；Android 工程相对路径现在均以 `apps/android/` 为基准。

### 迁移验证

在 macOS / JDK 21 上完成：

- 根目录 wrapper 入口 `bash apps/android/gradlew -p apps/android --version` 正常，固定版本仍为 9.7.1。
- `apps/android/` 中 `:app:assembleDebug :app:assembleRelease lint testDebugUnitTest --no-daemon --console=plain` 通过；45 项 JVM 测试，失败、错误、跳过均为 0。lint 无错误，保留现有警告。
- debug 与 unsigned release APK 均通过 `scripts/verify-apk.py`，包含启动／JNI 入口、四 ABI native 和许可证。
- 从仓库根目录使用 `-p apps/android/testing/sdk-client` 构建独立验收客户端通过。
- CI YAML、Android 工作目录与产物路径、PowerShell 语法和脚本默认 APK 路径检查通过。
- 迁移前的 156 个已跟踪文件均已核对去向；除明确更新的路径、文档与 CI 配置外，内容哈希一致，含 Gradle wrapper JAR 和两种启动脚本。
- 本地 Markdown 链接、迁移后构建产物与本地配置的忽略规则、`git diff --check` 通过。

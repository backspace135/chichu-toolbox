# 彳亍工具箱

面向 Android、Windows 和 macOS 的工具箱。**v0.1.0 仅交付 Android 端获取临时 Root 功能**，权限获取、适配检测、日志和结果验证围绕该流程提供支撑。

Android M1 已交付内置兼容官方 Shizuku SDK 的 Shell 权限服务；M2 开发中，已加入只读设备诊断与本地内核配置文本校验。桌面端属于后续阶段，范围为开源强制解锁 Bootloader 方案整合与固定上游全功能迁移，按 M0–M6 推进；技术栈已定为 Tauri 2 + React + TypeScript + Rust，macOS 仅适配 Apple Silicon，工程待初始化。固定功能清单、M1 平台/工具链输入和每日上游跟踪实现已准备，候选与采用状态分别记录，不代表功能已交付。

## 仓库结构

```text
apps/
  android/          Android 客户端、Gradle 工程、验收脚本和测试客户端
  desktop/          Windows / macOS 共享桌面工程（待初始化）
  windows/          Windows 平台说明入口
  macos/            macOS 平台说明入口（仅 Apple Silicon）
docs/
  android/          Android 技术栈、设计、实施计划和验收记录
  desktop/          桌面端技术栈与后续设计、验证记录
  assets/           产品图标与设计资源
third_party/        上游来源快照与许可证
.github/workflows/  各平台 CI
```

目录约定见 [仓库布局](docs/REPOSITORY_LAYOUT.md)。Android 与桌面端分别维护构建工具、模块、脚本和测试；Windows、macOS 共用桌面工程。

## Android 快速构建

需要 JDK 21、Android SDK 37 和 Build Tools 37.0.0。设置 `ANDROID_HOME`，或在 `apps/android/local.properties` 中设置 `sdk.dir`。

在仓库根目录执行：

```powershell
.\apps\android\gradlew.bat -p apps/android :app:assembleDebug lint testDebugUnitTest --no-daemon
```

```sh
bash apps/android/gradlew -p apps/android :app:assembleDebug lint testDebugUnitTest --no-daemon
```

APK 输出：`apps/android/app/build/outputs/apk/debug/app-debug.apk`。详细命令与设备验收入口见 [Android 开发说明](apps/android/README.md)。

## 客户端与文档

- [Android](apps/android/README.md) · [桌面端](apps/desktop/README.md) · [Windows](apps/windows/README.md) · [macOS](apps/macos/README.md)
- [Android 文档](docs/android/README.md)
- [桌面端技术栈决定](docs/desktop/TECH_STACK.md)
- [桌面端 UI 规范与确认预览](docs/desktop/UI_DESIGN.md)
- [桌面端详细开发文档与实施里程碑](docs/desktop/DEVELOPMENT.md)
- [桌面端固定功能基线与任务映射](docs/desktop/FEATURE_INVENTORY.md)
- [桌面端平台与初始化基线](docs/desktop/PLATFORM_BASELINE.md)
- [桌面端每日上游跟踪与验收缺口](docs/desktop/UPSTREAM_TRACKING.md)
- [桌面端总任务 #10](https://github.com/backspace135/chichu-toolbox/issues/10)
- [Android UI 规范与交互预览](docs/android/UI_DESIGN.md)
- [M2.5 阶段交付与验证](docs/android/M2_5_PROGRESS.md)
- [上游源码与来源](third_party/README.md)

未实施或未执行真机验收的能力不代表已经可用；实际验证结果见 [Android M1 验证记录](docs/android/M1_VALIDATION_RESULTS.md)与 [M2 开发记录](docs/android/M2_PROGRESS.md)。

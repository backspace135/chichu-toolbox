# 彳亍工具箱

面向 Android、Windows 和 macOS 的工具箱。Android M1 已交付内置兼容官方 Shizuku SDK 的 Shell 权限服务；M2 开发中，已加入只读设备诊断与本地内核配置文本校验。Windows、macOS 客户端待开发。

## 仓库结构

```text
apps/
  android/          Android 客户端、Gradle 工程、验收脚本和测试客户端
  windows/          Windows 客户端入口（待开发）
  macos/            macOS 客户端入口（待开发）
docs/
  android/          Android 技术栈、设计、实施计划和验收记录
  assets/           产品图标与设计资源
third_party/        上游来源快照与许可证
.github/workflows/  各平台 CI
```

目录约定见 [仓库布局](docs/REPOSITORY_LAYOUT.md)。各客户端独立维护自己的构建工具、模块、脚本和测试。

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

- [Android](apps/android/README.md) · [Windows](apps/windows/README.md) · [macOS](apps/macos/README.md)
- [Android 文档](docs/android/README.md)
- [Android UI 规范与交互预览](docs/android/UI_DESIGN.md)
- [M2.5 阶段交付与验证](docs/android/M2_5_PROGRESS.md)
- [上游源码与来源](third_party/README.md)

未实施或未执行真机验收的能力不代表已经可用；实际验证结果见 [Android M1 验证记录](docs/android/M1_VALIDATION_RESULTS.md)与 [M2 开发记录](docs/android/M2_PROGRESS.md)。

# 彳亍工具箱

原生 Android 工具箱，整合权限服务、临时 Root 和 Root 管理。当前实施 M1：内置兼容官方 Shizuku SDK 的 Shell 权限服务。

## 构建

- JDK 21；Android SDK 37，Build Tools 37.0.0。
- Gradle wrapper 固定 9.7.1，下载时校验 SHA-256。
- 设置 `ANDROID_HOME`，或在未纳入 Git 的 `local.properties` 中设置 `sdk.dir`。

Windows：

```powershell
.\gradlew.bat :app:assembleDebug lint testDebugUnitTest --no-daemon
```

Linux / macOS：

```sh
bash ./gradlew :app:assembleDebug lint testDebugUnitTest --no-daemon
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。构建不依赖 `third_party/upstream/`；该目录只供上游源码调研。

## 文档

- [技术栈](docs/TECH_STACK.md)
- [M1 服务设计](docs/M1_SHIZUKU_DESIGN.md)
- [M1 界面设计](docs/M1_UI_DESIGN.md)
- [M1 实施计划](docs/superpowers/plans/2026-11-08-m1-implementation.md)
- [M1 实施记录](docs/M1_PROGRESS.md)
- [M1 验收矩阵](docs/M1_ACCEPTANCE.md)
- [M1 验证结果](docs/M1_VALIDATION_RESULTS.md)
- [官方 SDK 验收客户端](testing/sdk-client/README.md)

默认只读设备检查：`./scripts/verify-m1.ps1 -Serial <serial>`。安装和激活需要明确指定 `-InstallDebug`、`-Activate`；不会自动停止原版或更改其他应用授权。

未实施或未执行真机验收的能力不代表已经可用；实际验证结果见实施记录。

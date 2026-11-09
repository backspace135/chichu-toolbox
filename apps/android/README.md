# Android 客户端

原生 Android 工具箱，整合权限服务、临时 Root 和 Root 管理。M1 已交付内置 Shell 权限服务，设备验证范围见 [M1 验证结果](../../docs/android/M1_VALIDATION_RESULTS.md)。M2 开发中，目前新增只读设备诊断与独立 Linux 内核配置文本校验，见 [M2 开发记录](../../docs/android/M2_PROGRESS.md)。

## 工程与构建

在 Android Studio 中打开本目录 `apps/android/`。

- JDK 21；Android SDK 37，Build Tools 37.0.0。
- Gradle wrapper 固定 9.7.1，下载时校验 SHA-256。
- 设置 `ANDROID_HOME`，或在本目录未纳入 Git 的 `local.properties` 中设置 `sdk.dir`。
- native 构建使用 NDK 29.0.14206865、CMake 3.31.6。

以下命令均在 `apps/android/` 中执行。

Windows：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease lint testDebugUnitTest --no-daemon
```

Linux / macOS：

```sh
bash ./gradlew :app:assembleDebug :app:assembleRelease lint testDebugUnitTest --no-daemon
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`，release 为 `app/build/outputs/apk/release/app-release-unsigned.apk`。

APK 启动与 JNI 入口检查：

```sh
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

构建会从仓库根目录 `third_party/licenses/` 读取许可证；不依赖用于源码调研的 `third_party/upstream/`。

## 模块

| 模块 | 职责 |
| --- | --- |
| `:app` | Compose UI、导航和应用入口 |
| `:core:common` | Android 端共享状态与流程模型 |
| `:core:shizuku` | 内置 Shell 权限服务 |
| `:core:ghostlock` | M2 设备信息与内核配置校验；临时 Root 尚未实现 |
| `:core:ksu` | KernelSU 通信与 Root 管理（M2/M3） |

`core/` 属于 Android 客户端，不代表跨平台共享库。

## 设备诊断与配置校验（M2 部分实现）

- 「设备诊断」页显示设备型号、系统版本、安全补丁、ABI、完整内核 release/version 和构建指纹；进入前台时重新检测。
- 通过系统文件选择器导入独立 `.config` / `.config.gz`，源文件与解压文本均限 2 MiB；校验 UTF-8、配置项语法、重复项及 gzip CRC，展示配置项统计和解压后原始字节的 SHA-256。
- 不要求 Shell 权限；不修改文件，不持久化导入内容。取消文件选择会保留现有结果；选定新文件后会清除旧结果，失败时只显示本次错误。
- `.config` 格式有效不表示设备支持；当前不解析 GhostLock 执行 profile，也不从镜像或 OTA 提取配置。

核心逻辑单测：

```sh
bash ./gradlew :core:ghostlock:testDebugUnitTest --no-daemon
```

## 设备验收

默认只读设备检查：

```powershell
./scripts/verify-m1.ps1 -Serial <serial>
```

安装和激活需要明确指定 `-InstallDebug`、`-Activate`；不会自动停止原版或更改其他应用授权。脚本默认 APK 路径以脚本自身目录为基准，从其他工作目录执行也可使用。

- [官方 SDK 验收客户端](testing/sdk-client/README.md)
- [Android 技术与验收文档](../../docs/android/README.md)

# M1 固定上游

| 项目 | 固定提交 / 依赖 | 许可 |
| --- | --- | --- |
| RikkaApps/Shizuku | `b844bc491f1790c72328e1a8e5b2349f8978f0ea` | Apache-2.0 |
| RikkaApps/Shizuku-API | `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5` | MIT |
| 官方公开 API/provider | Maven `dev.rikka.shizuku:api:13.1.5`、`provider:13.1.5` | MIT |
| miuix | Maven `top.yukonga.miuix.kmp:*:0.9.4` | Apache-2.0 |

移植到 `apps/android/core/shizuku/src/main` 的范围：Shizuku 的 server、starter、common，以及 Shizuku-API 的未发布 server-shared、rish；native 部分来自 manager JNI 和 rish CPP。无线调试移植 manager/adb 下的 AdbClient、AdbPairingClient、AdbKey、AdbMessage、AdbProtocol、AdbException 六个 Kotlin 文件，保留 JNI 关联包名。公开 api、aidl、shared、provider 不复制，沿用 Maven 传递依赖。

许可文本保存在 `licenses/`。未修改的上游注释保持原样；本项目新增代码注释使用中文。

## 派生改动

- 管理器为 `com.chichu.toolbox`，MANAGER 权限为自有 signature 权限。
- 独立进程名 `chichu_shizuku_server`、启动器 `libchichu_starter.so`、Shell 配置 `chichu-toolbox-shizuku.json`；启动器只重启同名内置进程，不停止原版。
- 服务仅接受 Shell UID 2000，M1 不混入 Root 激活路径。
- 第三方授权以工具箱自有配置为准，不导入共享 API_V23 的系统授权、不修改原版共享权限状态。
- 官方 SDK 的 Binder descriptor、事务编号、额外字段 key、Provider 通信协议保持原样。
- 管理事务增加调用者校验，额外提供管理器可读的服务身份事务，防止连接归属误判。
- 服务类直接编入主 APK，native 启动器设置 CLASSPATH 后执行 app_process；没有独立 server dex 或 start.sh。
- 配对码不持久化；ADB 私钥通过 Android Keystore 加密后存于应用私有偏好设置，移除上游私钥与帧数据日志。
- 管理器以自有连接持有 Binder，验证归属和 UID 2000；服务端一次性 token 绑定客户端身份并支持同步确认，防止伪造 Intent 和重放。
- 原版 Provider 在已有活 Binder 时忽略新 Binder；共存连接顺序不得描述为后推覆盖，实际矩阵仍待设备验证。

## 验证状态

本地完整构建、测试和 lint 已通过，Android 12 已完成电脑激活、官方 SDK 基本授权和 UserService 验收。无线与共存矩阵仍未验证。MediaTek UserService 增加缺失绑定身份初始化，避免空进程名导致 OEM 资源优化崩溃。详细证据见 [M1_VALIDATION_RESULTS.md](../docs/android/M1_VALIDATION_RESULTS.md)，本文件不代表全量验收已完成。

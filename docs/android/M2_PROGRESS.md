# M2 开发记录

任务：[M2 #4](https://github.com/backspace135/chichu-toolbox/issues/4)。工程根目录为 `apps/android/`。

## 当前交付范围

基于 `f13e7ed` 的 M1 基线继续开发。M2 尚未完成；本轮交付设备诊断与独立 Linux 内核配置文本校验，不代表 GhostLock 临时 Root 集成完成。

| 内容 | 状态与实现 |
| --- | --- |
| 设备信息 | 已实现：型号、代号、Android/API、安全补丁、ABI、构建指纹 |
| 完整内核信息 | 已实现：`Os.uname()` 的 release/version；保留全部后缀，不将数字版本等同于兼容性 |
| 检测生命周期 | 每次前台恢复与手动刷新时读取新快照；旧检测取消后不能覆盖新结果；快照不落盘 |
| 配置导入 | 系统文件选择器读取独立 `.config` / `.config.gz`；不申请存储权限，不持久化 URI 或原文件 |
| 配置校验 | 严格 UTF-8、Kconfig 赋值/禁用项、重复项拒绝、gzip 解码与 CRC、SHA-256 和分类统计 |
| 输入限额 | 源文件与解压文本分别限制为 2 MiB，最多 50,000 项、单行最多 8,192 字符 |
| 界面 | 新增「设备诊断」页，沿用现有 miuix、深浅色主题和宽屏导航；具备读取中、错误、成功状态 |
| 权限需求 | 仅使用本应用进程与公开系统元数据，不调用 Shell、su 或外部管理器 |

导入的是普通 Linux 内核配置文本，不是 GhostLock 的执行 profile。配置语法校验成功只说明文本可解析，不能证明来源、完整性、设备兼容性或运行能力。SHA-256 的输入是解压后的原始字节，包含原换行符与 BOM；同一文本的纯文本/gzip 文件结果相同。

## 尚未实现的 M2 项目

- GhostLock 上游执行逻辑/profile 资源整合与确定性支持匹配。
- 从镜像/OTA 提取候选配置。
- 内置 ksud、KernelSU 与管理通道的独立运行状态探测。
- 临时 Root 任务执行、进度日志与四项运行结果验证。
- 已有 Root 分支、重启后的 Root 状态重检及 Xiaomi 15 真机闭环验收。

当前界面将设备支持、Root、KernelSU、管理通道显示为未验证/未探测，不由配置格式或应用 UID 推断这些状态。

## 本地验证（2026-11-09，JST）

环境：macOS、Temurin JDK 21、Android SDK 37、NDK 29.0.14206865、CMake 3.31.6。

已执行：

```sh
ANDROID_HOME="$HOME/Library/Android/sdk" bash ./gradlew \
  :app:assembleDebug :app:assembleRelease lint testDebugUnitTest --no-daemon
```

后续将字符串值校验改为迭代扫描，避免长字符串的递归正则匹配，并增加对应测试；修改后执行：

```sh
ANDROID_HOME="$HOME/Library/Android/sdk" bash ./gradlew \
  :app:assembleDebug :app:assembleRelease :core:ghostlock:lint :app:lint \
  testDebugUnitTest --no-daemon
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

验证结果：

- debug 与经 R8 压缩的 unsigned release 构建成功。
- JVM 单测共 61 项通过，失败/错误/跳过均为 0；其中 M2 新增 16 项：版本解析 3 项，配置校验 13 项。
- 测试覆盖完整 release 后缀、拒绝不完整/溢出版本、配置类型统计、固定摘要、gzip/纯文本一致性、BOM/CRLF、重复项、非法 UTF-8/二进制/非法值、损坏 gzip、输入与解压上限、恰好等于大小上限、行长/数量上限、长字符串。
- lint 通过。`:core:ghostlock` 无问题；`:app` 为 0 errors、15 warnings，涉及现有依赖版本、上游 TrustManager、备份资源及构建建议等。
- 本机 SDK 存在 `android-35-2` 包位置不一致警告，构建成功。
- release APK 的启动/JNI 入口、四 ABI native 与许可证检查通过。

本轮没有执行设备安装、UI 真机检查或运行状态验收；JVM/构建结果不能替代真机证据。

本地证据：

- `core/ghostlock/build/test-results/testDebugUnitTest/`
- `core/ghostlock/build/reports/lint-results-debug.txt`
- `app/build/reports/lint-results-debug.html`
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`

## 待执行的只读功能验收

1. 打开「设备诊断」，核对设备、完整内核与系统构建信息；刷新、后台恢复后应重新检测。
2. 导入相同文本的 `.config` 与 `.config.gz`，核对统计和摘要一致。
3. 先导入有效配置，再导入损坏/重复项/超限文件，确认旧成功结果不残留。
4. 取消文件选择，确认原导入结果保持；进程重建后确认没有持久化导入结果。
5. 检查浅色、深色、宽屏、大字体下的长 release、构建指纹和 SHA-256 排版。

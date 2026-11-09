# M1 验证结果

日期：2026-11-08。工作分支 `feat/m1-permission-service`，基线 `b734cd5`。设备验收时尚未提交、推送或修改远端 issue；随后用户确认新图标并明确要求通过 PR 合并、关闭 #2。

## 本地构建

以下命令以 `apps/android/` 为工作目录；下方日志和设备结果是 M1 当时的验证记录。

```powershell
.\gradlew.bat testDebugUnitTest :app:assembleDebug lint :app:assembleRelease --continue --no-daemon --console=plain
.\gradlew.bat -p testing/sdk-client assembleDebug --no-daemon --console=plain
python scripts/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
python scripts/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

- 主工程最新日志：`C:\Users\a1358\AppData\Local\Temp\opencode\chichu-pr-final.log`，新图标替换后 `BUILD SUCCESSFUL`；此前审查修复日志为 `chichu-review-final.log`。
- JVM XML 汇总：45 项，失败 0、错误 0、跳过 0；无测试的占位模块不计作测试覆盖。
- lint 无错误，但仍有上游 hidden API、ADB trust manager、旧版本判断等警告；不是“零警告”。
- release APK 为 unsigned；已有下述压缩 DEX 设备运行证据，但未安装完整 release 包。
- 独立源码副本 `chichu-clean-9490cf9e` 不含 `.git`、upstream、构建目录、local.properties；四 ABI native 重新编译及 debug 打包成功。该副本早于最终审查修复，尚未用最新版重做独立副本构建。允许使用正常全局 Gradle 依赖缓存，不声称完全离线或禁用缓存构建。
- debug／release APK 均通过 `verify-apk.py`：DEX 启动／JNI 类、四 ABI 的 `libchichu_starter.so`、`librish.so`、`libadb.so` 与 `assets/OPEN_SOURCE_NOTICES.txt` 存在。检查类存在不等于运行行为已全覆盖。

## 真机基本协议

用户明确批准安装 debug、启动内置 Shell 服务和验收。设备 M2007J22C / cannon，Android 12 API 31，arm64-v8a。只修改自建 `com.chichu.toolbox.acceptance` 的授权，没有修改现有第三方应用授权、停止原版或使用 Root。

| 场景 | 实测 |
| --- | --- |
| 电脑 ADB 激活 | starter 返回 0；独立 `chichu_shizuku_server` UID 2000；界面显示已连接 |
| 官方 Provider attach | `BINDER=true PERMISSION=-1` |
| 未授权调用 | `UNAUTHORIZED_PROCESS_BLOCKED=true` |
| 真实请求拒绝 | `REQUEST_RESULT=-1`，列表显示已拒绝 |
| 真实请求批准 | `REQUEST_RESULT=0` |
| 批准后命令 | `SHELL=uid=2000(shell)... EXIT=0` |
| UserService | `USER_SERVICE_UID=2000`；解绑销毁出现断开回调，之后 ps 无验收服务进程 |
| 撤销 | 恢复询问后新客户端 `PERMISSION=-1`，执行再次被拒 |
| 授权持久化 | 批准后更新验收 APK、重启内置服务仍返回 `PERMISSION=0`；撤销只作用于验收 UID |
| 管理事务 | 未批准和已批准客户端的 10001–10004 均 `MANAGER_BLOCKED` |
| 伪造 token | 用真实 action／extra key 发送伪造 token，仅显示“授权请求已失效”，不出现批准入口 |
| 真实请求期间伪造 token | 修复后真实弹窗保留，点击拒绝仍收到 `REQUEST_RESULT=-1` |
| 无活客户端时撤销 daemon | 验收客户端 force-stop 后 probe PID 19671 仍存活；工具箱撤销后进程消失，新客户端 `PERMISSION=-1` 且执行被拒 |
| 服务死亡 | 仅停止工具箱自有 PID，冷启动工具箱显示未激活；重新执行 starter 恢复 |
| 基础浅色 UI | 稳定截图已检查：浅灰背景、白色分组、蓝色主操作、手机底栏，无 Root 假开关 |

设备日志涵盖约 21:19–21:52；日志和截图保留于本机批准临时目录，不作为长期记忆。

## MediaTek UserService 回归

首次 UserService 启动失败，异常为 `LoadedApk.makeApplication` 对空 String 调用 `equals`。显式给测试客户端设置 Application 类未能解决，因此撤销该无效实验，没有要求客户端改变 SDK。

对照 Android `ActivityThread.currentProcessName()` 与上游问题 [Shizuku #1198](https://github.com/RikkaApps/Shizuku/issues/1198)，定位 app_process 缺少常规 `mBoundApplication`，导致进程名为空。只在绑定数据缺失时初始化本辅助进程身份，再创建默认 Application；不调用第三方 Application.onCreate，不改变真实 UID。修复后同一未修改 SDK 客户端回调 UID 2000，并完成销毁。

该回归有真实设备失败→修复→成功证据，但没有覆盖所有 OEM／Android 版本的自动化单测；hidden API 兼容仍需其他设备验证。

## 最终审查回归

| 项目 | 证据与覆盖边界 |
| --- | --- |
| C1：release JNI 类被裁剪 | 修复前脚本实测缺少 `RishTerminal`；两个 rish 类显式 keep 后 debug／release 检查通过，并加入 CI；压缩服务运行证据见下节 |
| I1：UID 回收继承授权 | 当前包身份验证模型通过；未在设备实际制造 UID 回收，不能将模型测试表述为完整设备安全验收 |
| I2：daemon／排队服务撤销 | 安装包与客户端并集模型通过，daemon 真机撤销通过；排队启动交错未做设备压力测试 |
| I3：授权弹窗异步完成隔离 | 状态模型通过；真实弹窗被伪造 token 干扰的设备回归通过；双真实请求确认交错仍仅有模型证据 |
| I4：NSD 串行解析与代际 | 两类发现共享队列、迟到结果丢弃、重试上限模型通过；尚无设备自动发现端到端证据 |

部分新增测试先观察到类型缺失造成的编译失败，并非全部观察过断言失败；不声称全部严格 RED→GREEN。仍保留一项低优先级问题：无线激活成功后 Binder 死亡可能留下旧成功文案，实际服务状态与操作门禁仍按离线处理。

## 压缩 release DEX 设备运行（部分验收）

- 将最新 unsigned release APK 推到 `/data/local/tmp/chichu-m1-release.apk`，直接由 app_process 加载其中 DEX，显式指向已安装 debug APK 的 native 库目录。管理器仍为 debug 安装版，不修改安装签名或包数据。
- PID 19945／UID 2000 持续存活；官方 SDK 13.1.5 新客户端 attach 成功，未授权调用被拒，10001–10004 在批准前后均被拒。
- 在工具箱仅批准验收包后：`SHELL=uid=2000(shell)... EXIT=0`、`USER_SERVICE_UID=2000`，解绑出现 `USER_SERVICE_DISCONNECTED`。
- 撤销验收包后：`PERMISSION=-1`、`UNAUTHORIZED_PROCESS_BLOCKED=true`。随后使用正式 debug 安装路径的 starter 恢复服务，保留验收包为询问／未授权。
- 最初把临时 APK 传给 starter 时，其按安装布局推导的 `/data/local/tmp/lib/arm64/librish.so` 不存在；这是临时验收布局错误。修正为显式 native 路径后上述运行通过，不将此前失败归因于正式安装版。
- 此证据验证压缩服务 DEX 与当前设备 native 的协作，不覆盖完整 release 安装、压缩管理 UI、release 内置 ADB 配对或所有反射路径。

## 仍未覆盖

- 无线配对／连接／自动发现端到端。上次正确输入后配对端口返回 `ECONNREFUSED`，需重新打开系统弹窗并提供新参数；不记录配对码。
- 原版共存的两种安装、服务启动顺序和客户端归属矩阵；没有自动安装或干预原版。
- Android 17 本地网络权限实际拒绝／批准行为。
- 完整 release APK 安装运行；深色、动态色、大字体、宽屏与键盘完整视觉矩阵。横屏侧栏已有 UI 层级观察，不算完整视觉验收。
- 设备整机重启；没有为验收自动重启用户手机。
- 图标 MCP 预览：设计 MCP 不可用，Playwright 两次失败，未取得有效 MCP 审图结论。

## 图标确认与交付裁定

- 早期字形渐变图标被用户否决；新稿为纯蓝白几何工具箱，用户查看预览后确认采用。SVG 与 adaptive／monochrome 使用相同三段主体路径。
- 本机 Python Playwright 使用已安装 Edge 成功渲染大图、32／48／64 px、圆形／圆角方形和单色预览；不是 Better Design MCP 审查，也不是 Android 启动器实测。
- 用户要求合并并关闭 #2；这是交付裁定，不是对上述未覆盖项目的验收证明。PR／issue 说明继续保留缺口。

上述缺口未完成前，不宣称 M1 全量验收完成；按用户明确要求关闭 issue 时，应同时列出未覆盖项目，而非勾选为通过。

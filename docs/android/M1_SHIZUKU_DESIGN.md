# M1 设计：Shizuku 整合

> 2026-11-08 实施修订：纠正早期对独立 dex／start.sh 和 Provider 覆盖行为的假设。固定版本见 [M1_UPSTREAM.md](../../third_party/M1_UPSTREAM.md)，实施范围见 issue #2。

## Shizuku 的身份与连接机制（源码事实）

三条链路构成完整机制，整合方案围绕它们展开：

**① 角色识别——按权限声明，不看包名**（`server/.../BinderSender.java`）

server 运行时监听全系统进程 / UID 启动事件（`registerProcessObserver` / `registerUidObserver`），对每个启动的 App 检查其 manifest 声明的权限：

| App 声明的权限 | protectionLevel | server 的动作 |
| --- | --- | --- |
| `moe.shizuku.manager.permission.MANAGER` | `signature`（同签名才授予） | 认作管理器，推送 binder |
| `moe.shizuku.manager.permission.API_V23` | `dangerous`（用户批准） | 认作 API 客户端，推送 binder |

**② binder 送达——manager 包名硬编码**（`server/.../ShizukuService.java:503`）

```java
static void sendBinderToManger(Binder binder, int userId) {
    sendBinderToUserApp(binder, MANAGER_APPLICATION_ID, userId);  // "moe.shizuku.privileged.api"
}
```

binder 经目标包的 ContentProvider（`<pkg>.shizuku`）以 provider call 方式主动推入，不需要 App 先发起请求。

**③ 当前启动链路**：服务类直接编入管理器 APK。APK 打包 native 可执行文件 `libchichu_starter.so`，由 Shell 执行后设置 `CLASSPATH` 指向当前安装 APK，再运行 `app_process` 的服务入口。`ServiceStarter` 是 UserService 的 Java 启动／中转入口，不是独立小 APK。本工程不生成 `start.sh`，不打包独立服务 dex。

**上游构建结构事实**：主仓库 `settings.gradle` 将 `:aidl` `:rish` `:shared` `:api` `:provider` `:server-shared` `:hidden-api-stub` 的 projectDir 指向 `api/` 目录（源码实际位于独立仓库 Shizuku-API，默认从 maven 拉取；`local.properties` 中 `api.useLocal=true` + `api.dir=...` 可指向本地 checkout）。本项目 API 层走 maven 依赖（`dev.rikka.shizuku:api`），仅源码整合 server 端，与此结构一致。

## 整合改动

### 身份隔离与启动

| # | 改动 | 位置 |
| --- | --- | --- |
| 1 | `MANAGER_APPLICATION_ID` 常量改为 `com.chichu.toolbox` | `ServerConstants.java`，影响 `ShizukuService.sendBinderToManger` 与授权存储 |
| 2 | `MANAGER` 权限改用自有名 `com.chichu.toolbox.permission.MANAGER`（signature） | manager 侧 AndroidManifest 定义 + server 端 `BinderSender` 校验（`PERMISSION_MANAGER` 常量） |
| 3 | 独立 native starter、进程和配置名 | `libchichu_starter.so`、`chichu_shizuku_server`、`chichu-toolbox-shizuku.json` |

### 权限方案（关键决策）

- **`API_V23` 权限名保持 `moe.shizuku.manager.permission.API_V23` 原样**。它是 dangerous 级、由系统授予、不受签名限制；第三方 App 使用的官方 Shizuku SDK 声明的正是此名——保持不变是「第三方 Shizuku 客户端零改动连接」（验收标准第 2 条）的机制基础。
- **`MANAGER` 权限必须换名**。Android 对同名自定义权限以先安装者为定义者：用户先装原版 Shizuku（Rikka 签名）时，签名不同的本工具箱将永远无法获得 Rikka 定义的 signature 权限，server 亦无法认出我们这个管理器。换用自有权限名后，签名自洽，授予必然成功。
- 内置服务授权以自有配置为准，不读取或修改共享 `API_V23` 的系统授权。Shell 与后续 Root 授权使用独立类型，服务入口只允许 UID 2000。
- 管理器不用官方全局连接；自有 Provider 接收 Shell Binder 后清除入站身份，以管理器 UID 完成归属校验。管理事务验证调用 UID，第三方确认只接受服务端签发、绑定请求 UID／PID／requestCode、五分钟内单次使用的 token。

## 共存与冲突处理

原版与内置服务使用不同进程名，设计上互不查杀。官方 Provider 在已持有活 Binder 时忽略后续推送，并非「后推者覆盖先推者」。第三方实际连接归属取决于启动／送达顺序及重连行为，仍需设备验收，不能承诺透明共存。

处理策略（v0.1）：**检测 + 提示，不自动干预**。当前界面只能可靠检测原版包已安装，会明确说明尚未确认原版服务运行；不以安装状态冒充运行状态，不停止或接管原版。

## 验收映射

| 验收标准（issue #1） | 本设计的支撑 |
| --- | --- |
| #2 第三方客户端能发现并连接 | `API_V23` 权限名不变 + API 层走官方 maven SDK |
| #4 失效后重新激活引导 | 当前 APK 路径生成的 native starter 命令、无线调试配对和连接；死亡事件清理在线状态 |
| #5 与原版共存不冲突 | 管理器、进程、配置隔离与共存提示；实际第三方连接顺序尚待设备验收 |

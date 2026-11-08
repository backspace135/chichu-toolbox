# M1 设计：Shizuku 整合

> 2026-11-08 定案。基于上游源码核查（Shizuku 快照见 [third_party/SOURCE_SNAPSHOT.md](../third_party/SOURCE_SNAPSHOT.md)），对应 issue #1 里程碑 M1 与验收标准第 2、4、5 条。

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

**③ 启动链路**：manager APK 内打包 server dex 并生成 `start.sh`（`app_process` + `BOOTCLASSPATH`），电脑 adb 执行；`starter` 是独立小 APK，充当无线调试流程中被 `am` 拉起的入口；`shell` 模块为 rish 远程 shell 加载器。

**上游构建结构事实**：主仓库 `settings.gradle` 将 `:aidl` `:rish` `:shared` `:api` `:provider` `:server-shared` `:hidden-api-stub` 的 projectDir 指向 `api/` 目录（源码实际位于独立仓库 Shizuku-API，默认从 maven 拉取；`local.properties` 中 `api.useLocal=true` + `api.dir=...` 可指向本地 checkout）。本项目 API 层走 maven 依赖（`dev.rikka.shizuku:api`），仅源码整合 server 端，与此结构一致。

## 整合改动

### 必改三处

| # | 改动 | 位置 |
| --- | --- | --- |
| 1 | `MANAGER_APPLICATION_ID` 常量改为 `com.chichu.toolbox` | `ServerConstants.java`，影响 `ShizukuService.sendBinderToManger` 与授权存储 |
| 2 | `MANAGER` 权限改用自有名 `com.chichu.toolbox.permission.MANAGER`（signature） | manager 侧 AndroidManifest 定义 + server 端 `BinderSender` 校验（`PERMISSION_MANAGER` 常量） |
| 3 | `start.sh` 路径与 server dex 打包位置跟随 `com.chichu.toolbox` | `/sdcard/Android/data/com.chichu.toolbox/start.sh` |

### 权限方案（关键决策）

- **`API_V23` 权限名保持 `moe.shizuku.manager.permission.API_V23` 原样**。它是 dangerous 级、由系统授予、不受签名限制；第三方 App 使用的官方 Shizuku SDK 声明的正是此名——保持不变是「第三方 Shizuku 客户端零改动连接」（验收标准第 2 条）的机制基础。
- **`MANAGER` 权限必须换名**。Android 对同名自定义权限以先安装者为定义者：用户先装原版 Shizuku（Rikka 签名）时，签名不同的本工具箱将永远无法获得 Rikka 定义的 signature 权限，server 亦无法认出我们这个管理器。换用自有权限名后，签名自洽，授予必然成功。

## 共存与冲突处理

原版 Shizuku server 与本工具箱 server 为不同 `app_process` 进程，**可同时运行**。此时一个声明了 `API_V23` 的第三方 App 会被两边同时推送 binder，后推者覆盖先推者，连接归属不确定。

处理策略（v0.1）：**检测 + 提示，不自动干预**。UI 检测原版 server 运行中即向用户提示（「检测到原版 Shizuku 服务运行中，第三方应用可能连接到它」），符合 issue #1「不静默接管」原则；是否停用原版由用户决定。

## 验收映射

| 验收标准（issue #1） | 本设计的支撑 |
| --- | --- |
| #2 第三方客户端能发现并连接 | `API_V23` 权限名不变 + API 层走官方 maven SDK |
| #4 失效后重新激活引导 | `start.sh` / starter / dex 链路完整内置 |
| #5 与原版共存不冲突 | 包名、权限名、路径全隔离；双 server 检测提示 |

# M3 开发与验证记录

任务：[M3 #3](https://github.com/backspace135/chichu-toolbox/issues/3)。协议、身份、签名与共存边界见 [M3 整合记录](M3_KERNELSU_INTEGRATION.md)。

## 交付（2026-11-09，JST）

- `:core:ksu`：KernelSU UAPI 6 JNI 桥（探测、安全模式、app_profile 读写）、ksud 仓库层（UAPI 6 固定 `730f6446` + UAPI 5 固定 v3.3.1 双通道）、严格 JSON 模块解析、策略模板与操作日志。
- 应用侧：设置内「Root 管理」弹层（状态 / 超级用户与策略 / 模块 / 维护 / 身份与迁移 / 日志）、模块 WebUI（按模块独立 HTTPS 虚拟源、WebMessage 桥、CSP 与目录逃逸拦截）。
- 签名与资源：v2-only 签名（禁 v1/v3/v4 与 dependenciesInfo）、`ksu_identity.py` 提取内核绑定参数、构建与 CI 校验 ksud 二进制摘要与 APK 入口。
- 测试：19 项 `:core:ksu` JVM 单测、3 项签名解析 Python 单测、一次性 `M3Instrumentation` 设备验收入口与本地模块夹具。

## 本地验证

```sh
# 工作目录 apps/android/
bash ./gradlew :app:assembleDebug :app:assembleRelease lint testDebugUnitTest --no-daemon
python3 scripts/test_ksu_identity.py
python3 scripts/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release.apk
```

全部通过；debug/release 均为 debug 签名的压缩测试包（v2-only，证书 744 字节 / `7ba84aa1…1418`，仅本机有效）。

## 真机证据

### cannon（M2007J22C，Android 12，未 Root）

- 内置 ksud 3.3.1-38-g730f6446 版本读取正常；JNI 探测 errno -19（无驱动通道），页面如实显示管理通道未就绪。
- `su` 被 SELinux 拒绝时展示真实错误，不显示假成功。
- 压缩 release 安装运行正常，管理操作禁用原因清晰。

### dada（24129PN74C，Android 16，KernelSU 32680 / UAPI 5 / 临时加载，原版 Manager v3.3.1）

- 原版 Manager 显示「工作中 [越狱模式]」，用户批准后仅对 `com.chichu.toolbox` 授予 Root；工具箱 `su -c id` 返回 UID 0。
- 已有 Root 通道验证：读取真实内核信息（version 32680、flags 0x5、UAPI 5），自动选用 UAPI 5 ksud；工具箱未取得管理器身份，超级用户策略入口保持禁用并说明原因。
- 通过工具箱同 UID 的 ksud 完整执行（夹具 `chichu_m3_acceptance`）：ZIP 安装 v1 → 禁用 → 启用 → 更新 v2 → action（返回 UID 0 与标记文件）→ 卸载标记 → 撤销卸载。ksud 5 将安装内容暂存为待更新，未为测试重启设备；action/WebUI 所需文件仅在模块目录内就绪。
- WebUI：页面加载，`ksu.exec` 经来源受限桥返回 `uid=0(root)`；`fetch` 外部 https 被阻止，webroot 外路径返回 404，`../module.prop` 由 realpath 拦截；CSP 与每模块独立 host 均生效。
- 与原版共存：原版安装、授权与管理关系全程未变，工具箱未静默接管。

设备证据保存在本地 `docs/assets/ui-validation-20261109/m3-*.png|xml` 与验收临时目录，不纳入 Git。

## 已知 UI 问题（转 M3.5）

- WebUI 确认框与结果弹层叠加时布局拥挤、标题遮挡按钮。
- WebView 自动请求 `favicon.ico` 失败时错误文案盖住页面内容；子资源 404 不应按整页失败展示。

## 尚未完成（不满足 #3 关闭条件）

1. 工具箱签名绑定的内核/LKM 与配套用户空间资源链；当前固定 ksud 内嵌原版签名绑定 LKM，直接安装/修补启动镜像入口禁用。
2. 管理器身份下的超级用户策略写入读回（需配套内核将 `com.chichu.toolbox` 加冕为 manager；策略代码、读回校验与 JVM 模型测试已就绪）。
3. 原版共存顺序矩阵、debug 内核迁移与迁移后身份验证（本机无 CONFIG_KSU_DEBUG 参数）。
4. M2 临时 Root → M3 完整闭环（依赖 M2 提权执行引擎）。
5. ksud 原版模板库、SELinux 规则编辑与 WebUI 完整协议（spawn、文件 IO、包管理 API）。

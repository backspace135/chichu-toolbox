# M3 KernelSU 管理通道整合

日期：2026-11-09（JST）。任务：[M3 #3](https://github.com/backspace135/chichu-toolbox/issues/3)。

## 2026-11-10 配套测试构建（JST）

- 用户已允许维护项目专用 KernelSU 补丁；原始 `third_party/upstream/kernelsu` 保持不变。`apps/android/scripts/prepare-ksu-source.py` 只接受固定提交的构建副本，修复脚本退出状态丢失及 late-load 后工具箱 Activity 启动入口。
- Linux 构建目录：`taichang:/home/taichang/chichu-toolbox-build/kernelsu`。DDK 镜像 `ghcr.io/ylarod/ddk-min:android15-6.6-20260828`，摘要 `sha256:51159bc935c75a282fc65bc87e02f16bd098d1cdf55dc558c4fdd0fcf7a132e2`。以 `CONFIG_KSU=m`、`KSU_PACKAGE_NAME=com.chichu.toolbox`、证书长度 `0x02e8` 和下文测试证书摘要构建；未启用 debug 迁移或禁用身份认证。
- arm64 内核模块编译及 `check_symbol` 通过；x86_64 编译通过但缺少完整 `Module.symvers` / `vmlinux`，未完成符号及运行验证。仅包含 `android15-6.6`，不能作为其他 KMI 的通用资源。
- 用户空间使用 `scripts/ksu-build.Dockerfile`（Rust 1.91.1、NDK r29）和 `scripts/build-ksu-userspace.sh`。两 ABI 的 check / clippy / release 构建通过，ksuinit 与对应 LKM 内嵌；APK 资源哈希已更新。arm64 SHA-256 为 `8d4668b424fb513bda2de963c4c8e14499c78687b2c60f1eb6fdd49fb22a9122`，x86_64 为 `7f47621783a7900fd3d94aed93b9e2268a192b7a062cae0eead1f62e2bfa7ed9`。
- **上述只证明测试构建可生成，不证明管理器身份已生效。** dada 的 Bootloader 锁定、Verified Boot 为 green，当前是原版 UAPI 5 临时 Root；未刷写、未重载内核、未执行 GhostLock 漏洞提权。管理器身份、授权策略与迁移闭环仍未验收，镜像安装入口继续禁用。
- 下面的版本资源描述为初次 M3 交付记录；当前 UAPI 6 二进制已由上述配套测试构建替换，UAPI 5 二进制仍为原版固定资源。

## 初次交付版本与资源

- 源码固定为 `tiann/KernelSU@730f644615969cfd4a84b45d7f62a667b7b95a50`，UAPI 6、`app_profile` v4。
- `:core:ksu` 的 JNI 是对该提交 `manager/app/src/main/cpp/ksu.cc`、`kernel/include/uapi/` 的小型派生实现；保留实际 errno，不沿用上游把失败返回为空列表或默认策略的路径。
- ksud 来自该提交的 [Build Manager run 42329879308](https://github.com/tiann/KernelSU/actions/runs/42329879308)，未修改上游源码或内嵌资源。版本为 `3.3.1-38-g730f6446 (uapi: 6)`。
- 二进制放在 `apps/android/core/ksu/src/main/jniLibs/`，构建前验证 SHA-256：

| ABI | Artifact ID | SHA-256 |
| --- | --- | --- |
| arm64-v8a | 13557345891 | `ffc7e995d85f0f7d6fb85ad2f3d41709eba458553307680550c23ae581588199` |
| x86_64 | 13557540726 | `2171f23748b4e54a48a0046a7fec40b06cd4f2ce5b788dd376bd44078ab77bc4` |

32 位 ABI 保留只读 JNI 探测，但不提供 ksud；界面明确报告缺少用户空间资源。没有把 64 位可执行文件伪装为 32 位资源。KernelSU GPL-3.0 许可证随 APK 的开源声明分发。

## 身份与签名

上游 `kernel/manager/throne_tracker.c` 同时依赖编译时包名和 APK 签名；`GET_INFO` 的 `MANAGER` 标志才是当前进程被认可的证据。`GET_APP_PROFILE` / `SET_APP_PROFILE` 的权限是 **only_manager**，UID 0 本身不能替代该身份。

工具箱使用 `com.chichu.toolbox`，与原版 `me.weishu.kernelsu` 独立。签名使用上游同版本 `lsplugin-apksign 1.4`：可通过 Gradle 属性 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 配置；未配置时本地与 CI 使用 debug keystore，包括 release 构建。M4 尚未确定发布签名，**当前 release 是压缩测试包，不是正式发布包**。

固定内核的 APK 解析器只接受一个 v2 block 与 verity padding；会拒绝 v3/v3.1 以及 AGP dependency block。因此构建显式关闭 v1/v3/v4 和 `dependenciesInfo`；`verify-apk.py` 对 debug/release 均检查签名块。

提取配套内核的构建参数：

```sh
# 工作目录 apps/android/
python3 scripts/ksu_identity.py app/build/outputs/apk/release/app-release.apk
```

输出 `KSU_PACKAGE_NAME`、`KSU_EXPECTED_SIZE` 和 `KSU_EXPECTED_HASH`。摘要是 v2 块中 DER 证书的 SHA-256，不是整个 APK 的 SHA-256；长度是证书字节数。脚本检查容器边界和签名块布局，不替代 Android 的密码学验签，也不证明当前内核已接受管理器。

本机测试证书长度为 744（`0x02e8`），SHA-256 为 `7ba84aa188e91b87bdc931e5a102d289e6f524a871c9294c6fd20821a1d11418`。该值仅属于本机构建；其他机器和未来发布签名必须重新提取。

### 可以管理与不能接管

| 条件 | 行为 |
| --- | --- |
| 无可见 `[ksu_driver]` | 报告 errno 和未确认状态；不宣称设备没有 Root |
| 驱动存在、UAPI 不一致 | 禁用管理，报告两个版本 |
| UAPI 一致、无 MANAGER 标志 | 报告未识别身份，不使用普通 Root 绕过 |
| MANAGER + UAPI 一致 + ksud 实际返回 UID 0 | 开放管理 |
| 已有非 KernelSU Root | 可主动请求 `su -c id -u` 验证；不要求先经过 M2，不自动获得 KernelSU 管理身份 |
| 原版正式内核 | 不能在线更换签名绑定，需重新构建配套内核 |
| CONFIG_KSU_DEBUG 且已有 su 授权、迁移参数可写 | 用户确认后执行固定 ksud 的 `debug set-manager com.chichu.toolbox`；重新打开并验证 |

JNI 只扫描当前进程已有驱动 FD，不在 Android App 内主动调用 reboot 系统调用申请 FD，避免触发应用 seccomp。没有驱动与没有 Root 保持语义独立。

## 授权、策略与记录

- KernelSU 是持久化允许列表，没有 Shizuku 的“待询问”语义。允许、拒绝、撤销都写真实内核策略；撤销恢复默认非 Root 策略，后续请求不允许。
- 同 UID 共享授权，提交前重新核对包 UID；绑定当前包名/UID 后写入，再读回比较。通信失败或读回不一致时显示错误，不做乐观成功更新。
- 策略支持 UID、GID、附加组、Linux capabilities、SELinux 域、挂载命名空间与 NO_NEW_PRIVS。已有自定义策略的简单编辑保留其他字段。
- 工具箱模板存于私有 SharedPreferences；支持保存、载入、删除和应用。JSON 输入校验对应 UAPI 限额；不是原版 ksud 模板库的隐式导入或迁移。
- 操作记录存于私有 `root-operations.log`，含 JST 时间、ksud 退出码和 stdout/stderr；可选择复制。内核 Root 调用日志从现有 `/data/adb/ksu/log/` 读取，不自动开启 sulog。
- 撤销不会把已运行 Root 进程自动降权；页面明确要求重启目标应用后验证新请求。

## 模块、WebUI 与维护

- ksud `module list` 严格解析 JSON 字符串标志；空数组和通信失败不同。支持 ZIP 安装/更新、启停、卸载、撤销卸载和 action；变更后重新读取真实列表。
- WebUI 使用 Compose `AndroidView`。打开前明确确认 Root 信任，每模块独立 HTTPS 虚拟源；只为本地可信主文档开放 WebMessage 桥，不使用会暴露给所有 frame 的 `addJavascriptInterface`。
- 请求路径 URL 解码后校验，再由 Root `realpath` 检查模块 webroot 内包含关系；拒绝 webroot 符号链接、目录逃逸、外部来源、远程脚本、子框架、file/content 和 mixed content。
- 当前兼容 `ksu.exec`（空 options）、`moduleInfo`；尚不提供完整上游 `spawn`、文件 IO、包管理等扩展 API。非空 exec options 明确报错。没有可用安全消息桥的系统 WebView 显示更新提示。
- 按模式开放用户空间安装和启动镜像恢复；临时加载/内建模式不开放启动镜像写入。
- **直接安装、更新和镜像修补尚未完成**：当前 ksud 内嵌原版签名绑定 LKM。入口禁用并说明原因，不能把其当成工具箱配套内核刷入。

## M2 → M3 联调契约与缺口

M2 尚未实现临时 Root 执行引擎。它后续必须使用相同源码/UAPI、工具箱签名绑定的 LKM 与配套 ksud，并正确重启 `com.chichu.toolbox/.MainActivity`；上游 late-load 默认仍引用原版 Activity 名，当前没有使用它启动工具箱。

M2 完成后必须独立验证：临时 Root 执行结果、内核加载、MANAGER 标志、ksud UID 0；不能把提权命令退出 0 当成四项全部通过。已有 Root 分支可直接执行相同探测。

本轮是 M3 管理器基础与受限功能实现，**不满足 Issue #3 的完整关闭条件**。仍需：

1. 构建工具箱签名绑定的内核/LKM 与完整用户空间资源链，并完成镜像处理。
2. 在有可用 KernelSU 的真机验证 Root 允许/拒绝/撤销、策略、模板、模块和 WebUI。
3. 验证原版共存顺序、debug 迁移与迁移后身份，记录失败恢复方式。
4. M2 临时 Root 到 M3 的完整闭环。
5. WebUI 完整协议、ksud 原版模板及 SELinux 规则编辑按后续实际需求补齐。

构建和真机证据见 [M3 开发记录](M3_PROGRESS.md)。

## GhostLock 整合契约与 4.14 适配（2026-11-16，JST）

关联：#37。

### GhostLock 合法整合通道

- GhostLock（com.ghostlock.app 1.2）不自带 LKM：设置里可选择任意已安装应用作为根管理器，运行时执行该应用 `libksud.so` 的 `late-load --kmi`，装载其构建时内嵌的 LKM。MANAGER 身份由 LKM 编译期绑定包名与签名证书（`KSU_PACKAGE_NAME`/`KSU_EXPECTED_SIZE`/`KSU_EXPECTED_HASH`，本仓库 `scripts/prepare-ksu-source.py` 与配套构建即其来源）。
- 这就是受支持的第三方管理器整合契约：不刷写、不关闭内核签名校验；工具箱以自己内嵌的、绑定工具箱的 LKM 取得 MANAGER。在 dada 上切 GhostLock 根管理器为工具箱并重跑后，内核上报 32718 / UAPI 6 / manager，允许、拒绝、撤销、重新请求、自定义 UID 与策略读回全矩阵通过（见 M3 开发记录）。
- 验收客户端 `testing/sdk-client` 新增 `RootProbeActivity`：每次启动由独立进程发起一次真实 `su -c id`，以退出码与输出作为证据。工具箱声明 `QUERY_ALL_PACKAGES`（管理器需枚举全部应用做策略管理）。

### cannon（Redmi Note 9 5G，4.14.186）内核适配

- 将 730f6446 的 kernel/ 移植到 4.14：syscall 经典参数签名分发（`task_pt_regs` 自取 + `KSU_SYSCALL_CALL` 六参展开）、lsm_hook `list_head` 分支、`path_umount` 桩、SELinux 层改走内核原生 `security_load_policy` 重载（策略结构 `struct selinux_ss` 映射）、`__pte_to_phys`/`copy_*_kernel_nofault`/`strncpy_from_user_nofault`/`copy_*_user_nofault`/`__poll_t`/`TWA_RESUME`/`kvcalloc`/`minmax.h` 等 API 兼容、`selinux_cred`/`selinux_inode`、avtab flex_array、filename_trans 哈希表、`type_attr_map` flex_array、`seccomp_cache` 桩（4.14 无动作缓存，setresuid 路径直接装驱动 fd）、`su_mount_ns` 经 mount(2)、`pkg_observer` 4.14 fsnotify `handle_event`、`selinux_hide` 4.14 桩化、`file_wrapper` 4.14 用 `anon_inode_getfile`。
- 配置以真机提取的 `stock-config.gz` 为基（LCM/TEE/GPU 与真机一致），树内源码版本对齐 GPU r25p0 / TEE 300；工具链镜像补齐 Debian clang-14 缺失的默认 CFI 忽略名单。
- 构建材料固化于 `apps/android/scripts/cannon/`：`ksu-4.14-compat.patch`（全部移植差异）、`cannon-build.sh`（4 CPU / 8 GiB / 低 CPU shares 资源约束）、`docker/Dockerfile.cannon` 与 `docker/cfi_ignorelist.txt`、`baseline.patch`（UDI 修复 + fts8719 固件）、`stock-config.gz`。

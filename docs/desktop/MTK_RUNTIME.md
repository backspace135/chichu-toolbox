# M0b：MTK 路线调查、运行时与验收

记录日期：2026-11-10（JST）；对应 [#17](https://github.com/backspace135/chichu-toolbox/issues/17)。

**交付状态：macOS 运行时调查测试通过；用户要求合并本次交付并关闭 #17，原双平台/真机验收条件仍未满足。** 已实现并实测 macOS arm64 独立 CLI 启动和 USB 枚举；没有任一平台从锁定到解锁的项目证据。Windows 主机 SSH 连接被关闭，未构建 Windows 包。以下版本是调查候选，未变更 `scripts/desktop-upstream/adopted.json`，不得进入正式推荐目录。

## 固定来源与范围

| 来源 | 固定版本 | 用途 / 结论 |
| --- | --- | --- |
| [mtkclient](https://github.com/bkerler/mtkclient/tree/118a000a83ea31c4f427fe5225abfe964546e05e) | `118a000a83ea31c4f427fe5225abfe964546e05e`，CLI 2.1.4 | 未修改上游；冻结 CLI、Python 模块及 payload；vendor Loader 不进入可执行资源目录 |
| [Jz8Root](https://github.com/Jz8Root/xiaomi-hyperos-bootloader-unlock/tree/679709d1cd6ab9f5b41465dcedd9d42ee605586f) | `679709d1cd6ab9f5b41465dcedd9d42ee605586f` | README 的 LK/RPMB、失败设备与 Fastboot 验证依据；仅引用研究，没有复用扫描器或 RPMB 写入代码 |
| [xiaomi_ubl](https://github.com/18xjul08/xiaomi_ubl/tree/7bde56dcb0a0aefd7807c76d28c0044e78aa6b60) | `7bde56dcb0a0aefd7807c76d28c0044e78aa6b60` | Snapdragon/ENG ROM 资源指引，不是 MTK 实现，也不能证明 Xiaomi 15 已支持 |
| MiUnlockTool | 见每日调查清单 | 官方账号路线，不纳入本强解候选 |

### 当前真实设备

本次 `adb devices -l` 只发现一台 MTK Android 设备；使用显式 `-s` 查询只读属性：

```text
ro.product.model=M2007J22C
ro.product.device=cannon
ro.board.platform=mt6853
ro.build.fingerprint=Redmi/cannon/cannon:12/SP1A.210812.016/V14.0.6.0.SJECNXM:user/release-keys
ro.boot.flash.locked=0
ro.boot.vbmeta.device_state=unlocked
ro.boot.verifiedbootstate=orange
```

这些是 Android 属性前态，非本次解锁或权威 Fastboot 验证。未重启、写分区、清数据或恢复锁定。缺少该子型号/构建的 LK、活动 slot、loader/preloader、BootROM 实际交换和初始锁定证据，不能仅凭 `mt6853` 推定可用。

## M3 适配输入

当前 **已验证方案集合为空**。`cannon` 仅是待调查精确组合，不能成为“适用”推荐；已解锁前态排除重复解锁。后续目录行须至少固定：

- 产品型号/子型号、SoC 与实际 hwcode、ROM fingerprint、补丁级别、活动 slot、两个 LK 的 SHA-256、preloader/DA 的 SHA-256；每项带采集来源和时间。
- BROM 或 preloader 的真实进入记录、SBC/SLA/DAA 条件、利用及 DA 路径、授权/证书要求、Windows 驱动/主机权限、平台运行时摘要。
- Windows/macOS 分别独立的锁定→执行→Fastboot 解锁证据；工具升级或 LK 改变后重新验证。

mtkclient 固定 README 明确 MT6781/6789/6855/6886/6895/6983/8985 的 patched BROM/V6 差异和指定 loader/preloader 路径，不能把 BROM 利用通路扩展到所有 MTK。一般按断电、按音量键、插入 USB 进入 BROM，但精确按键和窗口必须在选定机型上确认，不能由本文当作已验证步骤。

Jz8Root 固定 README 表明 MIUI14/HyperOS 的部分 LK 用 RPMB 覆盖 seccfg，且存在 early-boot 恢复 magic 的失败机型。扫描 LK 的结果只是候选条件；不能由 SoC、系统大版本、扫描分数或字符串缺失自动授权 RPMB 擦除。本次没有选择 RPMB 路线，不导入其扇区常量，也不实现自动轮试。

## M4 执行契约（待真机冻结）

1. 创建任务时固定设备身份、连接路径、构建/LK/slot、工具清单和资源摘要；单设备排他。VID/PID 不是唯一身份，多个低层设备或跨模式身份不明时暂停并要求重新确认连接，不能按枚举顺序选目标。
2. 用户开始后才能进入模式或运行利用；BROM/DA 读分区也可能先上传并执行代码，不当作后台无副作用探测。每个阶段保存完整 stdout/stderr、退出码、参数数组与任务目录，Rust 用绝对路径直接启动进程，不交给电脑 shell。
3. 准备阶段先读 GPT，按真实分区名备份 seccfg、LK（包括存在的两个 slot）、preloader/boot 及 NV 相关分区；如确需 RPMB 路线须单独备份 RPMB。保存分区起止/长度、设备与构建、文件长度及 SHA-256。备份必须有本设备恢复依据，不承诺通用可恢复。
4. seccfg 候选的上游入口为参数数组 `["da", "seccfg", "unlock", "--loader", DA_ABSOLUTE_PATH]`，必要的 preloader/auth/cert 必须来自该设备证据。实际写入见固定源码 `Library/DA/xflash/extension/xflash.py:781–832`：读取 GPT 定位 seccfg、解析配置并生成数据，再写对应 user 分区偏移；legacy/XML 各有独立实现，不能只验一个后声称全部协议通过。
5. 当前未提供 DA，因此运行时仅可做自检/帮助，不能独立完成解锁。不得自动生成未知 seccfg；上游成功日志、返回值或进程 0 不证明最终解锁，适配器必须独立验证。上游部分失败仅打印信息，M4 不能只包装退出码。
6. 数据影响按可能丢失用户数据处理；userdata/metadata 擦除是另一个有明确影响的阶段，不能跟随通用模板默认执行。中断后重新采集状态，不自动续写、切路线或回锁；杀进程不代表设备回滚。
7. 最终进入本机型的 bootloader Fastboot，显式目标读取 `getvar unlocked`，记录原始 stderr/stdout 和设备身份，须为 `yes`；`secure`、Android 属性及可用时的 `oem lks` 只作补充。再验证冷启动后的持久性。前态须同样有权威 locked/no 证据，不能用另一平台解锁后的读取代替。

这是一份后续适配器约束，不是已实现的任务执行器；#20/#21/#22 负责接入。

## 最小运行时构建

实现：`scripts/desktop-runtime/{entry,build,verify}.py`、`requirements.in` / `requirements.lock`。固定 CPython **3.13.11**、PyInstaller **6.22.3**；完整依赖和发布 SHA-256 在 lock 中。构建需 Python/Git/安装依赖，**产物启动不需要开发工具或在线 pip**。

macOS 构建复现（仓库根目录；uv 为构建工具，不随包）：

```sh
export UV_CACHE_DIR="$PWD/apps/desktop/build/uv-cache"
export UV_PYTHON_INSTALL_DIR="$PWD/apps/desktop/build/python"
export PYINSTALLER_CONFIG_DIR="$PWD/apps/desktop/build/pyinstaller-cache"
uv venv --python 3.13.11 apps/desktop/build/runtime-venv
git clone https://github.com/bkerler/mtkclient.git apps/desktop/build/mtkclient
git -C apps/desktop/build/mtkclient checkout 118a000a83ea31c4f427fe5225abfe964546e05e
uv pip install --python apps/desktop/build/runtime-venv/bin/python --require-hashes --only-binary=:all: -r scripts/desktop-runtime/requirements.lock
apps/desktop/build/runtime-venv/bin/python scripts/desktop-runtime/build.py --source apps/desktop/build/mtkclient --output apps/desktop/build/runtime
apps/desktop/build/runtime-venv/bin/python scripts/desktop-runtime/verify.py apps/desktop/build/runtime/dist/mtk-runtime
```

输出为 `apps/desktop/build/runtime/dist/mtk-runtime/` 整目录，不能只复制可执行文件。运行时绑定包内 `libusb-package` 动态库，缺失/无法加载直接报错，不回退到 Homebrew；`--runtime-check` 只枚举 USB 描述符，不打开/占用/写设备。GUI、mfusepy 和 vendor Loader 不进入运行时搜索目录，seccfg CLI 不要求 macFUSE/WinFsp。上游帮助仍显示 `fs`，该命令不是本运行时支持范围；上游会打印 `fuse library not installed`，不隐藏此信息。

Windows 在 **`D:\project\chichu-toolbox` 内**采用相同 Python/lock/脚本原生构建，将 venv Python 路径替换为 `build\runtime-venv\Scripts\python.exe`；`UV_CACHE_DIR`、`UV_PYTHON_INSTALL_DIR`、`PYINSTALLER_CONFIG_DIR`、`TEMP`、`TMP` 全部指向该项目 `build\` 下预先创建的目录。当前未执行，不能据通用脚本声明 Windows 构建通过。

### 权限、资源和分发许可

- Windows 上游要求 MTK 串口驱动与 UsbDk；打包 libusb 不等于已打通 UsbDk backend。本次未安装驱动、未核实签名/版本、未访问注册表。后续在项目目录获取固定官方安装器、记录来源/签名/摘要，单独征得驱动安装许可后检查 `UsbDkController -n` 及实际 BROM 握手。若需要上游专用 DLL，先核许可/摘要再纳入，不能由此包推定已支持。
- macOS 本次普通用户 USB 枚举成功；实际 BROM claim/interface 和 USB 配件授权仍待测。无需为 CLI 安装 macFUSE 或降低系统安全策略。签名、公证、下载隔离属性和最低 OS 未验收。
- 上游 Python 为 GPLv3；包内附其 LICENSE 和固定 commit 的完整源码 tar。该源码归档含上游原有资源用于对应源码留存，**不代表其中 vendor 二进制获得再分发授权**。本次仅本地调查，没有发布运行时。
- 其余依赖涉及 Python/PSF、PyInstaller GPL+bootloader exception、PyUSB BSD、pyserial BSD、colorama BSD、PyCryptodome 公有领域/BSD、libusb-package Apache-2.0、libusb LGPL 等；构建复制已安装分发的 license 文件。最终发行仍须核对实际 wheel、libusb 对应源码/替换义务、CPython 来源/许可和 payload 可重建来源，license 自动收集不等于法务清单完成。
- DA、preloader、LK/ROM、auth/cert 当前无选定来源/许可/摘要。不能随包的资源由用户从其合法持有的匹配官方固件导入，记录原始来源、文件名/长度/SHA-256、精确构建与 slot；没有合法可获取资源的组合保持阻塞，不能运行时下载匿名 loader 或上传用户授权文件。

## 本次测试证据

主机：macOS **26.6.2 / 25G83 / arm64**；Python 由 uv 下载项目内独立 CPython 3.13.11，不是 Homebrew Python。最终运行时可执行文件 SHA-256：

```text
mtk-runtime: 8c7793f0028df5d5c98a2e3f6dafb3dae47152e4bb112d70933c1a5477db6678
libusb-1.0.dylib: e9f7e2a6e6e63d7fa77d5aac7832f5795e637282582377466585f2439b50c96f
requirements.lock: 43b0da295f0eda9dd3593f4f473a8726888eda750160e88f66b5a6b6f9842545
```

`build.py` 原生构建成功。`verify.py` 实际输出：

```json
{"verified_files": 820, "arm64_macho_files": 84, "relocated_startup": "passed", "invalid_input": "nonzero"}
```

- 820 个包文件校验通过；84 个 Mach-O 均含 arm64，`otool -L` 未发现非系统绝对依赖。
- 将包复制到含空格的新目录，以空 PATH、无 PYTHONPATH/venv/DYLD 环境从其他 cwd 启动，自检、顶层帮助及 `da seccfg --help` 全部退出 0；无效参数非零退出。
- 自检确实加载重定位目录内 libusb，枚举 **21** 个 USB 设备；AES block size 16，pyserial 3.5，SHA-256 自检输出 `ef8f4ebc890e75379a849d9edcfbd1ed6ba602000ce205268323802bd4376e7a`。
- 首次 smoke test 发现 libusb 查询名不匹配并失败，已改为获取包内路径；随后完整校验通过。
- 这证明本机上隔离开发环境变量后的启动/动态加载，**不等于另一台无开发工具主机安装验收、USB BROM 握手或解锁**。
- `ssh g16win` 返回 `Connection closed ... port 10022`，远端命令未执行，未产生远端构建/驱动变更。

## 关闭阻塞与清理

- [ ] 确认两台等价的初始锁定 MTK 测试机，或对所选精确构建已有依据的恢复锁定流程；当前已解锁 cannon 不满足，不能为测试通用强制回锁。
- [ ] 获取精确 LK/DA/preloader/固件与合法资源摘要；确认进入方式、BootROM/loader/授权、备份和写入路线。
- [ ] Windows 主机恢复访问，原生运行时、驱动及实际 USB 通路测试；macOS 实际 BROM/DA 测试。
- [ ] 两端各完成锁定→解锁→权威持久后态记录，以及独立无开发依赖主机运行；正式采用/发布许可、最低 OS、签名与公证验证。
- [ ] 最终设备证据回填；实现提交见本次 PR。按用户明确要求关闭 #17，以上缺口不因此成为已通过，也不解除 M4 的真机证据依赖。

按用户要求，测试临时目录由 `verify.py` 退出时删除；本次下载源码、解释器、venv、uv/PyInstaller 缓存与运行时产物在测试后清理，仅保留上述源码、锁与验收记录。未安装全局 Python 包、驱动或 macFUSE，未启动服务。

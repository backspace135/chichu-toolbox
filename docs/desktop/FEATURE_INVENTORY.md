# gjgjxweb 固定基线功能与复用清单

盘点日期：2026-11-10（JST）。任务：[#14](https://github.com/backspace135/chichu-toolbox/issues/14)，范围以 [#10](https://github.com/backspace135/chichu-toolbox/issues/10) 审订正文为准。**这是源码盘点和本地任务映射，不是客户端实现或真机验收记录。**

清单共 **149个功能/行为ID + 6个核查项**：设备25、应用20、文件7、显示8、系统10、调节5、电池13、脚本35、终端8、设置18。一个ID内的有限枚举选项逐个列出、共同验收，不把同一个命令的每个取值重复计数。

## 1. 基线、证据与状态规则

- 上游：`CzJam/gjgjxweb`，固定提交 **`f84f9d50cf5ba63928192e64776081fd1cd9ea60`**；源码自报版本 `1.0.0`，不等于桌面版本号。
- [固定源码树](https://github.com/CzJam/gjgjxweb/tree/f84f9d50cf5ba63928192e64776081fd1cd9ea60)。下表所有 `文件:行号` 都相对该树，不引用浮动 HEAD。可用 `git show f84f9d50cf5ba63928192e64776081fd1cd9ea60:js/core.js` 等复核。
- 逐项读取 `index.html:18–38`、`js/app.js:1–162`、`js/core.js:1–86`、`js/batch.js:1–49`、`js/transport.js:1–35`、`js/terminal.js:1–84`、`server.cjs:1–16` 及三份许可；还对照 `docs/index.html` 的内联版本。内联 core/batch/terminal/app 与模块版本的业务一致；transport 的差异是 importmap/data URL 与本地 vendor 导入。页面差异是排版/链接空行，不新增业务能力。依赖里的 Fastboot/投屏等类不能当作产品功能。
- 简写：`H=index.html`，`A=js/app.js`，`C=js/core.js`，`B=js/batch.js`，`T=js/transport.js`，`X=js/terminal.js`。行很长时使用符号/动作名进一步定位。
- **每行继承如下版本/状态/证据字段（对全部 ID 逐项适用）**：目标版本=`待决定：首版或后续桌面版本，号码未定`；Windows=`未实现 / 未验收`；macOS arm64=`未实现 / 未验收`；实现提交=`无`；Windows 验收证据=`无`；macOS 验收证据=`无`。源码位置是行为证据，不能填入平台通过证据。后续实施必须逐项覆盖这些字段，不能以一个组通过代替单项通过。
- 默认条件：设备操作要求用户授权且在线的 ADB shell；主机文件操作要求用户选择的本应用可访问路径。`ROM` 表示取决于 Android 版本、厂商接口或 SELinux/权限，尚无本项目验证机型；不是已承诺通用支持。纯本地项不要求设备。输入拒绝和实际错误须可见，不能照抄上游吞失败/默认假值。
- 下表分组标题的「入口 / 归属 / 复用」也是每行字段。它们是六入口下的能力归属映射，**不批准新增页面层级、主操作或改主色**。新增控件设计仍按 UI 规范确认。每个 ID 只有一个验收主责，协作接口不重复计完成。

## 2. 设备、连接与模式

入口：**设备概览**（连接使用顶部设备上下文）。主责 **#23 / M5a**，连接底座协作 #19、模式任务 #20。复用 **R1/R2**。

| ID | 源码位置/符号 | 输入 → 实际行为及条件 |
| --- | --- | --- |
| DEV-01 | H:18,21; A:59; T:24–25 | 选择 USB 设备 → WebUSB 认证；允许取消；手机确认调试，浏览器接口占用报错。桌面改原生 ADB，不照搬结束其他 adb.exe 的提示 |
| DEV-02 | A:57–58; T:22 | 断开/连接取消 → 停脚本、电池统计、终端并释放连接；断连重置展示 |
| DEV-03 | H:22; A:61,80,144; T:10 | 刷新已授权 USB 列表、选序列号 → 手动连接目标；不是同时对多台发命令 |
| DEV-04 | A:60,161 | 自动连接唯一已授权设备；多台只提示手动选择；偏好归 SET-11 |
| DEV-05 | C:49 getInfo | 品牌 `ro.product.brand`、型号 `ro.product.model` → 展示真实属性 |
| DEV-06 | C:49 | 代号优先 `ro.product.odm.name` 后 `ro.product.device`；桌面保留来源而非假默认值 |
| DEV-07 | C:49 | Android release 与 SDK → 系统版本/API |
| DEV-08 | C:49 | `/sys/devices/soc0/serial_number` → 主板 ID；节点/权限 ROM 相关 |
| DEV-09 | A:55,79 | 隐藏/显示主板 ID → 仅展示遮罩，不改设备标识、不隐藏日志中的标识 |
| DEV-10 | C:49 | `ro.product.cpu.abi` → CPU ABI |
| DEV-11 | C:49 | `ls /dev/block` 的 sda/mmcblk0 → UFS/eMMC 推断；不能外推成精确存储型号 |
| DEV-12 | C:45,49 | `wm size/density` → 当前显示尺寸与 DPI |
| DEV-13 | C:49,29 | `/proc/uptime` → 天/时/分；读取失败不能以零天冒充 |
| DEV-14 | C:46,49,82 | `dumpsys battery` → 电量%、电压 V、温度 ℃ 摘要 |
| DEV-15 | C:49 | `uname -m` → 系统架构 |
| DEV-16 | C:49 | `ro.build.fingerprint` → 编译版本 |
| DEV-17 | C:49 | `uname -r` → 内核版本 |
| DEV-18 | C:47,28; A:64,71,151 | 各 CPU `scaling_cur_freq` kHz → MHz 定时刷新；仅主页且页面可见、不忙时；不可读须明确 |
| DEV-19 | A:64 | 点 CPU → `scaling_available_frequencies` 可用频率表 MHz；ROM 可能不公开 |
| DEV-20 | H:22; C:72; A:148 | 重启 → `reboot:`；上游只确认已发送，桌面 #20 要验证返回模式 |
| DEV-21 | H:22; C:72 | Bootloader → `reboot:bootloader`；条件取决设备 |
| DEV-22 | H:22; C:72 | Fastbootd → `reboot:fastboot`；设备须具备该模式 |
| DEV-23 | H:22; C:72 | Recovery → `reboot:recovery` |
| DEV-24 | H:22; C:72; A:148 | EDL → `reboot:edl`，确认文案限定部分高通；不是刷机/救砖 |
| DEV-25 | H:22; C:72; T:21 | 关机 → `reboot -p`，断连不直接证明关机完成 |

## 3. 应用、安装与提取

入口：**应用管理**。主责 **#24 / M5b**；文件流协作 #25、偏好 #30。复用 **R1/R3**。上游变更多数固定 `--user 0`，桌面不得悄悄推广为所有用户；新增用户选择属于真实增强。

| ID | 源码位置/符号 | 输入 → 实际行为及条件 |
| --- | --- | --- |
| APP-01 | H:23; A:95; C:51 | 已安装包列表、包名子串搜索、选包 → `pm list packages`；选中关闭自动填充；基线没有名称/图标/用户系统筛选列表 |
| APP-02 | A:69,71; C:27,50 | `dumpsys window windows`，无前台时读 activity → 包名/活动；软件页约 1.2 秒轮询 |
| APP-03 | H:23; A:28,69; C:8 | autoFill 开关 → 前台包名自动填入且输入只读；关闭后手输并校验包名 |
| APP-04 | C:53 packageAction(disable) | 包名 → `pm disable-user --user 0`，冻结 |
| APP-05 | C:53 enable | 包名 → `pm enable --user 0`，解冻 |
| APP-06 | C:53 launch | 包名 → monkey LAUNCHER 启动；需实际入口 |
| APP-07 | C:53 stop | 包名 → `am force-stop` |
| APP-08 | C:53–54; A:75 | 普通卸载 → 系统包加 `-k --user 0`，第三方 `--user 0`；不是统一删除系统文件 |
| APP-09 | C:53 uninstall-keep | 包名 → `pm uninstall -k --user 0` |
| APP-10 | C:53 clear; A:75 | 确认后 `pm clear --user 0` 清数据 |
| APP-11 | C:53 info; A:75 | 包名 → 原始 `dumpsys package` 软件信息弹窗；不是 APK 大小/总占用的统一数据源 |
| APP-12 | C:53 restore | 包名 → `cmd package install-existing --user 0`；仅仍存在系统包可恢复 |
| APP-13 | C:55; A:75 | 卸载锁读/切换/读回；仅第三方包；SDK→事务码：25→145、26/27→151、28→152、29→151、30→156、31/32→136、33→133、34→134、35→138、36→139；查询码为+1。非标准 ROM 必须验证，未知 SDK 拒绝 |
| APP-14 | A:53,115; C:53 | 成功的 packageAction 记录时间/动作/包名，最多500条；info 提前返回不记，提取/锁定也不在该 history 路径；历史不是全部任务日志 |
| APP-15 | C:9; A:93–94,146,153 | Google 19包多选/全选，逐项禁用/启用并汇报部分失败；包集合见下文 |
| APP-16 | A:96–111; C:77–79 | 一个/多个 APK → 普通安装逐个 `pm install -r`；多选不是 split，会逐项报告；临时文件清理 |
| APP-17 | C:77–80 install-multiple | 脚本关联多个 APK → install-create/write/commit；失败 abandon；无独立页面按钮，仍属基线可达语义 |
| APP-18 | C:77 | 安装 SDK≥34 加 `--bypass-low-target-sdk-block`；allowDowngrade 加 `-d`，其持久化 SET-06 |
| APP-19 | C:52,75 extract | 单路径 `pm path` → 含包名/版本的 APK 保存 |
| APP-20 | C:36,75 | 多路径 base/split 全部提取 → 无压缩 ZIP，保留各 basename；不能仅导出 base |

Google 包集合（APP-15，不能以“Google 全家桶”模糊替换）：`com.android.vending`、`com.google.android.inputmethod.latin`、`com.google.android.apps.podcasts`、`com.google.android.gm`、`com.google.android.gms`、`com.google.android.backuptransport`、`com.google.android.apps.docs`、`com.google.android.googlequicksearchbox`、`com.google.android.gsf`、`com.google.android.videos`、`com.google.android.apps.photos`、`com.google.android.apps.maps`、`com.google.android.music`、`com.google.android.apps.walletnfcrel`、`com.google.android.apps.subscriptions.red`、`com.google.android.apps.tachyon`、`com.android.chrome`、`com.google.android.youtube`、`com.google.android.apps.youtube.music`。可用性由设备实际已装包决定。

## 4. 文件与截图

入口：**文件传输**（概览快捷截图复用同一任务）。主责 **#25 / M5c**，截图最小闭环 #20。复用 **R3**，设置持久化 #30。

| ID | 源码位置/符号 | 输入 → 实际行为及条件 |
| --- | --- | --- |
| FILE-01 | A:112; C:73; T:18 | 多文件选择 → pushPath+文件名逐个推送，进度/逐项失败；设备路径须可写 |
| FILE-02 | C:74,80; T:19 | `adb pull 路径 [本地名]` → 读取并保存；脚本可达，网页无目录浏览器；上游768MiB整块收集限制不作为桌面流式实现要求 |
| FILE-03 | C:76; A:74 | 截图 → `/sdcard/时间戳.png`；关闭电脑保存时仅留手机 |
| FILE-04 | C:76 | 开启电脑保存 → 拉取并校验 PNG，再保存；校验失败留手机原图 |
| FILE-05 | C:76; A:50 | 删除手机截图选项 → 仅本地写入已确认成功才 `rm`；浏览器下载请求不算落盘成功 |
| FILE-06 | A:114,147 | 截图按钮右键/设置查看 → 本会话缩略图与再次保存；没有跨启动图库 |
| FILE-07 | A:50,128 | 选择下载目录 → 截图归 Screenshots，APK 归 ApkExt，其余根目录；未选目录走浏览器下载。桌面等价见第10节 |

## 5. 显示、系统界面、无级调节

入口：**设备概览**的现有设备工具能力分组（具体控件编排待实现时按 UI 规范确认）。主责 **#26 / M5d**。复用 **R1/R2**；截图归 FILE-03–06，不重复计数。以下均受 ROM/settings/input shell 权限限制；写命令成功不等于厂商界面生效。

| ID | 源码位置/符号 | 输入 → 实际行为及条件 |
| --- | --- | --- |
| DSP-01 | H:24; A:65; C:26,45 | 刷新分辨率/DPI/dp；取最后的 physical/override 作为当前值，保留物理值 |
| DSP-02 | C:57; A:81 | 宽/高整数100–16384 → 确认后 `wm size WxH`，读回 |
| DSP-03 | C:59; A:82 | `wm size reset` → 物理尺寸，读回 |
| DSP-04 | C:58 | DPI/最小宽度dp输入1–10000；dp用 min(W,H)*160/dp 四舍五入；最终 DPI72–1600 → `wm density` |
| DSP-05 | C:59; A:84 | `wm density reset` → 物理密度，读回 |
| DSP-06 | H:24; C:56 | 返回4、主页3、多任务187 → 对应 `input keyevent` |
| DSP-07 | H:24; A:147; C:56 | 锁屏26；右键长按 `KEYCODE_POWER --longpress` |
| DSP-08 | H:24; C:56 | 音量+24、音量−25、静音164 → 对应 keyevent |
| SYS-01 | C:10,60–61; A:68,154 | 勾选隐藏 clock/location/headset/cast/alarm_clock/volume/mobile/airplane/bluetooth/battery/wifi/nfc；合并 icon_blacklist，保留未受控 slot |
| SYS-02 | C:60; A:154 | 左下旋转提示勾选 → `show_rotation_suggestions=0`，不勾=1；与图标统一应用 |
| SYS-03 | C:60 | 时钟秒数 → `clock_seconds=1/0`；与图标统一应用 |
| SYS-04 | C:61–62; A:86,155 | 快捷图标数3–9/保持当前/恢复默认 → sysui_qqs_count put/不写/delete |
| SYS-05 | C:61–62; A:86,155 | 顶部间距0/5/10/15/20/30/保持/默认 → sysui_rounded_content_padding |
| SYS-06 | C:63; H:25 | 状态栏沉浸 → policy_control immersive.status=范围；空范围=* |
| SYS-07 | C:63; H:25 | 导航栏沉浸 → immersive.navigation=范围 |
| SYS-08 | C:63; H:25 | 双栏沉浸 → immersive.full=范围；支持包名逗号及 apps,-排除包语法 |
| SYS-09 | C:63 | 全部恢复 → `policy_control immersive.full=null`；不是恢复任意原始 policy_control |
| SYS-10 | C:64; A:87 | Wi-Fi ×/! 修复：确认后同时改 HTTP/HTTPS 小米 generate_204、time_zone=Asia/Shanghai、ntp_server=ntp1.aliyun.com；不能隐去后两项影响；是否拆分操作待产品确认 |
| TUNE-01 | C:65–66; H:27; A:67,149 | 字体倍率0.2–4，slider步进.04 → system font_scale，保留两位；刷新读当前值 |
| TUNE-02 | C:65–66; H:27 | 窗口动画0–4，步进.04 → global window_animation_scale；0关闭 |
| TUNE-03 | C:65–66; H:27 | 过渡动画0–4，步进.04 → global transition_animation_scale |
| TUNE-04 | C:65–66; H:27; A:67 | 程序动画0–4，步进.04 → animator_duration_scale；null上游展示默认1×，桌面区分未设置 |
| TUNE-05 | C:65,67; A:88 | 自动锁屏秒数1–2147483 → 四舍五入毫秒写 screen_off_timeout，读回 |

## 6. 电池

入口：**设备概览**的设备工具能力分组；主责 **#27 / M5e**。复用 **R1/R4**。模拟与真实值区分，dumpsys/sysfs 权限和 PowerDumper 条件逐机验证。

| ID | 源码位置/符号 | 输入 → 实际行为及条件 |
| --- | --- | --- |
| BAT-01 | C:25,46,82; A:66,71 | 刷新原始 dumpsys 与摘要；电压mV/1000，温度/10；电池页约2秒更新 |
| BAT-02 | C:68; H:26 | 温度−50～150℃ → 四舍五入×10写 temp，读回 |
| BAT-03 | C:68; H:26 | 电量整数0–100 → set level，读回 |
| BAT-04 | C:69 | 模拟非充电 → battery unplug；取消开关触发完整 reset |
| BAT-05 | C:69 | 模拟无线充电 → ac/usb=0、wireless=1、status=2；关闭 reset |
| BAT-06 | C:69 | 模拟USB充电 → usb=1、另两项0、status=2；关闭 reset |
| BAT-07 | C:69 | 模拟交流充电 → ac=1、另两项0、status=2；关闭 reset |
| BAT-08 | A:89 | 恢复真实参数 → dumpsys battery reset 并读回，不只改显示 |
| BAT-09 | A:71–72,90; H:26 | 开始/停止统计，1/2/3/5/10/30/60秒（默认3）；新开始清旧行，切页继续，断连停止；最多50000条，视图最近1500 |
| BAT-10 | C:70–71; A:90 | 可选功率：current_now×voltage_now/1e12 W，带符号；不可读尝试已装 com.jamcz.powerdumper 的 MainActivity power 输出；可能强停/启动该App；不自动下载安装；失败提示本次不记功率 |
| BAT-11 | A:72 | 统计 elapsed HH:mm:ss/电量/温度/可选功率；绑定一次采集设备与序列，不混设备 |
| BAT-12 | A:91; C:23 | CSV导出 UTF-8 BOM、CRLF、时间/电量(%)/温度(℃)/可选功率(W)，机型+时间命名；无数据报错 |
| BAT-13 | A:92; H:26 | 专注视图切换 → 仅统计展示，不改变采集 |

## 7. 控机脚本与 BAT 子集

入口：**终端与日志**内脚本能力；主责 **#28 / M5f**。复用 **R5**，APP/FILE/DEV命令调用其业务接口；目录授权与持久化协作 #30。不能交给电脑 cmd.exe、PowerShell 或 sh 执行。

| ID | 源码位置/符号 | 输入 → 实际行为及条件 |
| --- | --- | --- |
| SCR-01 | A:41–47,145 | 编辑、选脚本自动暂存、Tab插两空格、保存本地列表；唯一脚本名，非bat/cmd补.bat |
| SCR-02 | A:118 | 新建输入名称/作者 → chcp/echo off/名称作者日期模板；名称安全化，不执行模板主机命令 |
| SCR-03 | A:45,117 | 保存 → localStorage，选脚本目录时同名写文件；桌面用应用文件持久化 |
| SCR-04 | A:119 | 删除确认 → 本地列表及已选目录同名文件删除；不存在文件不妨碍删记录 |
| SCR-05 | A:48,120; H:34 | 多选bat/cmd/txt导入，UTF-8严格解码失败改GB18030；同名确认替换 |
| SCR-06 | A:121 | 编辑器内容 → UTF-8 BOM BAT 导出 |
| SCR-07 | A:122–123 | 选择读写脚本目录、刷新bat/cmd；外部内容冲突确认后覆盖当前编辑器 |
| SCR-08 | A:124; C:80 | 关联本地文件按名称/ basename查找供push/install/IF EXIST；同名后选替换；不是任意电脑目录访问 |
| SCR-09 | A:54,77,117; B:11,14–43 | 保存并运行，确认目标，逐行进度/完成/失败/停止；取消透传，断连停止 |
| SCR-10 | A:133,138 | 插入点击/长按/滑动，坐标0–16384、时长1–60000ms；长按以同起终点swipe表达 |
| SCR-11 | A:133,138 | 指针坐标显示/关闭 → 立即写 system pointer_location 1/0，不是只插入文本 |
| SCR-12 | A:134,138 | 插入主页/返回/多任务/锁屏/音量±/静音/截图/长按电源命令；截图写/sdcard/screen.png，不走截图下载偏好 |
| SCR-13 | A:135,138 | 插入启动包/启动活动/强停/卸载；包名或包/活动校验；启动活动am start -n |
| SCR-14 | A:136,138 | 插入文字（空格转%s）、重启、关机、Bootloader/Fastbootd/Recovery；input text主要英文数字符号 |
| SCR-15 | A:137–138 | 插入延时/循环，数值0–100000；循环变量与选区生成set/goto/if模板 |
| SCR-16 | C:8; A:138 | autoDelay → 非流程插入命令后追加1秒choice；设置持久化 #30 |
| SCR-17 | B:12,14,18 | UTF-8 BOM剥离、^续行、空行/rem/标签/@、echo off/chcp/setlocal/endlocal忽略；setlocal并无作用域语义 |
| SCR-18 | B:12,30 | set变量；名称不区分大小写；%name%/%%字母/!name!展开，未知变量保留原文；非通用cmd展开 |
| SCR-19 | B:8,29 | set /a =、+=、-=、*=、/=、%=；整数括号与加减乘除余，除法截断；除零/非法算式报错 |
| SCR-20 | B:28 | set /p → 对话输入，取消终止脚本 |
| SCR-21 | B:14,26–27 | 标签不区分大小写；重复/缺失报错；goto、goto:eof、exit[/b][数字]结束；不实现子程序返回栈 |
| SCR-22 | B:13,24–25 | IF not/defined/exist/errorlevel、==字符串、EQU/NEQ/LSS/LEQ/GTR/GEQ数字；单行仅goto/echo/adb/set/exit/call或多行括号；不宣称支持 /i、else |
| SCR-23 | B:15–16,21–23 | FOR /L %%字母 in(始,步,终) do 多行块；负步长可，0拒绝；IF/FOR括号预配对，错误给行号 |
| SCR-24 | B:31–35 | echo/echo.输出与^转义，echo >nul抑制；timeout/choice /t或/t:延时0–86400秒；pause弹框；cls忽略而非清终端 |
| SCR-25 | B:36 | 仅剥离尾部nul或匹配Log路径重定向，移除^转义；不生成电脑日志文件、不执行管道语法 |
| SCR-26 | B:37–38 | ADB命令失败设errorlevel，紧随受识别的errorlevel IF可接管，否则带行号失败；CALL只支持ADB，不支持子BAT |
| SCR-27 | B:18,39–43 | 最多100000执行步，每50步让出事件循环；未知FOR/IF/电脑命令报错，取消保留Abort；不吞语法错误 |
| SCR-28 | C:31,80 | ADB路径前缀、adb.exe、引号参数 → 适配；剥离-s/-d/-e后仍固定当前设备，桌面需明确告知且不得绕过任务目标 |
| SCR-29 | C:80 | adb shell / 非adb Service.execute输入 → Android shell；BAT Runner本身拒绝非ADB主机命令，不能混淆两者 |
| SCR-30 | C:80 | adb reboot/设备get-state/get-serialno/version/devices → 当前连接信息/操作；不返回电脑所有ADB设备 |
| SCR-31 | C:80 | adb uninstall[-k]、push、pull、install、install-multiple → APP/FILE接口及关联文件；不直接访问脚本给的电脑任意路径 |
| SCR-32 | C:80; T:20 | adb tcpip[端口1–65535，默认5555] / usb → 当前设备服务切换；**实际存在，但不是无线配对/连接UI** |
| SCR-33 | C:80 | adb disconnect关闭连接；wait-for-device仅检查已连接立即返回，并非等待发现 |
| SCR-34 | C:80 | adb exec-out screencap走screenshot业务，其余走shell文本；不是任意二进制exec-out兼容 |
| SCR-35 | C:80; B:39–40 | fastboot明确拒绝，未知adb动词报未适配；不能因依赖可用而默认扩展到刷写 |

## 8. 交互终端

入口：**终端与日志**，主责 **#29 / M5g**；生命周期与互斥 #20。复用 **R6**。

| ID | 源码位置/符号 | 输入 → 实际行为及条件 |
| --- | --- | --- |
| TERM-01 | X:14–19,40–65; T:14 | xterm-256color真实PTY，ANSI与键盘/二进制输入，非单次命令框；shellProtocol或noneProtocol |
| TERM-02 | H:29; X:17,67–70 | ↑↓历史/Tab补全透传设备Shell，由设备shell支持程度决定，没有应用命令收藏库 |
| TERM-03 | X:19,23,26,60 | 容器尺寸变化fit并PTY resize；绑定当前会话 |
| TERM-04 | A:116; X:20–21,72 | Ctrl+C/中断发送0x03，不等于杀主机进程；Ctrl+Shift+C复制 |
| TERM-05 | A:116; X:35–38 | 复制选区；无选区复制缓冲文本，scrollback5000 |
| TERM-06 | A:116; X:34 | 清屏清pending/xterm显示，不清任务记录或设备状态 |
| TERM-07 | A:76,116; X:40–44,74–79 | 切页保留会话，重开先关闭；断连关闭，退出码/失败可见；桌面切设备不得把已有会话改目标 |
| TERM-08 | X:24,28–33 | 未开终端时积存普通操作输出最多1500条；PTY活跃时不混入activity输出；桌面另存真实任务日志，不复用成全部日志已持久化 |

## 9. 设置与关于

入口：**设置**。主责 **#30 / M5h**。复用 **R7**；消费方分别 #23/24/25/28。默认值以 C:8 为准；仅存在的设置才提供控件。

| ID | 源码位置/符号 | 输入 → 实际行为及条件 |
| --- | --- | --- |
| SET-01 | H:30; C:11,32; A:62,152,159 | 默认启动页：上游10页；桌面迁移到六入口，display/system/battery/tuning→设备概览，scripts→终端与日志，about→设置；导入时保留能力定位信息，不能发明第七入口 |
| SET-02 | H:30; A:29,125; C:32 | 主题五配色teal/indigo/bluegrey/green/purple及循环；**与已确认固定主色体系冲突待决策，保留跟踪，不自动上线五色或删除能力**；深浅色不等于这五色 |
| SET-03 | H:30; A:30–32; C:32 | UI缩放80–120%，步进5；重排与终端fit |
| SET-04 | H:30; C:32; A:71 | CPU更新间隔默认1000ms，500–60000、四舍五入；DEV-18真实消费 |
| SET-05 | C:8,32; A:112,131 | 默认接收路径/sdcard/，绝对路径，禁NUL/换行，补末尾/；FILE-01消费 |
| SET-06 | C:8,77 | allowDowngrade默认false → 安装加-d，ROM允许与否仍需实测 |
| SET-07 | C:8,76; A:33,131 | screenshotDownload默认true；关闭同步关闭screenshotDelete |
| SET-08 | C:8,76 | screenshotDelete默认false；仅已确认电脑保存后删源，FILE-05消费 |
| SET-09 | A:128,50 | 下载目录选择与当前目录名；句柄仅内存、不跨启动持久化；桌面映射第10节 |
| SET-10 | A:125,131; C:32 | 保存按钮/设置change立即规范化保存，本地不可持久化时提示导出；桌面改应用设置文件，错误可见 |
| SET-11 | C:8; A:60 | autoConnectAuthorized默认true，唯一设备自动连接，多台手选；与ADB server密钥归属方案协同 |
| SET-12 | A:129; T:31 | 断开后确认清密钥 → 删除站点IndexedDB Tango；被其他标签阻塞报错；桌面只清本应用拥有的凭据，详第10节 |
| SET-13 | A:126 | JSON导出preferences；不含脚本、历史、目录句柄和ADB密钥，不宣称全量备份 |
| SET-14 | A:127; C:32 | JSON导入已知键同类型值，规范化页/缩放/主题/CPU/路径；无效JSON失败；桌面迁移差异要明确，不能宽吞错误 |
| SET-15 | C:8; A:28,69,149 | autoFill默认true持久化，APP-03消费 |
| SET-16 | C:8; A:138,149 | autoDelay默认false持久化，SCR-16消费 |
| SET-17 | C:8,32 | connectionMethod强制usb，clearInput/clearOutput默认false但无UI/消费方；仅遗留序列化键，不新增无效开关；导入处理由#30明确 |
| SET-18 | H:31; A:156–157 | 关于：作者六个社交链接、官网、项目/Tango/xterm归属、视频链接；版本1.0.0/引擎2.6.4/userAgent/静态更新日志/使用说明。桌面替换为实际自身版本、宿主、真实更新与支持说明，保留上游版权归属而非冒充原作者 |

## 10. 容易误记的边界与桌面等价行为

| ID | 固定源码证据与结论 | 桌面映射、任务/版本/平台状态 |
| --- | --- | --- |
| AUD-01 | T:26–30 有connectTcp/connectServer/bridgeRequest/byteChannel，A:1–162没有调用；H无无线字段；server.cjs仅静态HTTP，无/start-server、/adb、/server WS实现。vendor内wireless/pair类不构成产品配对入口 | 不称“基线已支持无线配对”。#19保留未接线接口调查，#28保留SCR-32；新增配对/mDNS/无线连接属于待排期桌面增强；不以删桥接源码掩盖事实 |
| AUD-02 | H及A无命令收藏数据模型/动作；X的↑↓交给Shell；scripts列表是脚本保存不是单命令收藏 | 无线外的“命令收藏”同样未证实；#28/#29记录增强候选，不计作基线已实现。若追加必须单独定版 |
| AUD-03 | A:123脚本目录、128下载目录均showDirectoryPicker(readwrite)，仅内存；A:45/119/122实际读写/删除 | #28负责脚本目录等价操作，#30负责路径选择与偏好，#25负责落盘；原生系统目录对话框授予明确所选路径，本应用记录/释放自己的路径引用，授权失效报错并可重选；不清浏览器或其他软件授权 |
| AUD-04 | T:24 CredentialStore('搞机工具箱')，31删除整个站点Tango库；A:129禁止连接中清除 | #30与#19先固定ADB server/密钥归属：仅对本应用拥有的密钥支持断开后清除及重新手机授权；若共享外部server，明确凭据由外部管理，不能删~/.android/adbkey或其他工具密钥，也不能伪报已撤销手机端授权；具体私有server/存储方案仍待#19定案 |
| AUD-05 | H:20各页、A动作、C:80拒绝fastboot；未发现投屏、分区刷写/完整救砖、官方账号解锁产品路径；依赖含相关类不能证明已有 | 不因EDL按钮扩大为救砖。若后续基线差异发现实际功能，#14/#16提交范围决策并分配任务，不静默丢弃 |
| AUD-06 | C:49可选读取吞失败、T:19整块pull，B有限错误接管等是实际源码语义 | 复用不能复制假默认值/吞失败。#23/#25/#28依本仓库边界错误规范实现，保留正常行为与明确的语义差异记录 |

AUD行沿用第1节目标版本待决、Windows未实现/未验收、macOS未实现/未验收与证据为空；它们是核查/决策跟踪项，**不混算可见功能数量**。目录路径是否跨启动保存、旧五色主题如何与当前主色规范兼容、Wi-Fi操作是否拆开，均未获产品确认。这里只固定行为要求和归属，不擅自决策。

## 11. 复用单元与原生接口契约

以下是待实施的最小接口职责，不是已存在 API、插件系统或要求整体重写 Rust。复用前保留版权与变更说明，不能拷贝上游目录而不处理许可。

| 单元 | 可复用来源 | 交接边界与实现责任 |
| --- | --- | --- |
| R1 命令/业务 | C:38–80 Service、quote/pkg/数值校验；Google与slot集合 | 保留命令构造、选项/用户0/恢复语义；设备执行经#19/#20原生接口：固定deviceId+taskId、command或业务参数、cancel、stdout/stderr/exitCode。业务不能直接访问电脑shell；内层不重复校验 |
| R2 解析/采集 | C:22–29、45–50、61、65 | props/battery/display/focus/cpu/单位换算可复用JS/TS纯函数；Rust采集真实输出并带来源/时间/失败状态；原始输出夹具对照；不迁移optional吞错与未知假值 |
| R3 文件/安装 | C:36、52、73–79；T:17–19 | 保留base/split清单、安装会话、PNG确认删除规则；原生fileId/remotePath/目标设备、流式传输、进度、校验、保存确认和产物记录。Zip/Blob整块收集不照搬；#24安装与#25共用传输，取消由#20管理 |
| R4 电池统计 | C:23,25,68–71；A:66,72,89–92 | 保留温度/电压/功率与CSV语义；采样/时钟/文件落盘由原生长任务持有，前端只展示数据；PowerDumper仅检测现有安装，资源采用另行查许可，不默认捆绑APK |
| R5 BAT与插入器 | B全部；A:41–48、54、77、117–124、132–138；C:31,80 | 优先复用解释器/编辑交互为TS；execute→带固定设备的业务接口，ask→可取消对话框，hasFile→用户关联fileId映射，output/progress→任务Channel。脚本整体占用设备；原生强制互斥，不信任前端锁；不执行电脑BAT |
| R6 终端 | X全部；T:14 | xterm输入/渲染/resize/复制逻辑可适配；Rust持有sessionId/deviceId/PTY与退出状态，输入/resize/中断显式会话；输出走Channel；切页不杀会话。设备Shell能力决定history/completion |
| R7 设置/持久化 | C:8,11,32；A:14,29–33,125–131,156–157 | 复用已知设置键与迁移规则；原生应用设置文件、系统路径选择、自己的凭据服务；JSON不带秘密；目录句柄替换为原生授权/所选路径，失败传用户，不模拟浏览器成功 |

## 12. 许可、版权与再分发安排

| 对象 | 已核查的固定证据 | 安排与未完成项（#14→#31发布核对） |
| --- | --- | --- |
| gjgjxweb自有业务 | LICENSE完整GPL v3；H:2–5及五个js/server/css文件SPDX为`GPL-3.0-only`、`2026 晨钟酱Official` | 实际复制/改编这些逻辑时按GPL-3.0-only准备整个相应派生作品的许可及Corresponding Source、构建/安装所需脚本、修改说明、版权与完整许可文本；不把“只署名”或进程分离当豁免。当前仓库未见根LICENSE，**项目最终发布许可待维护者确认**，本次不替用户改根许可 |
| Tango vendor | LICENSE-Tango.txt：MIT，Copyright (c) 2020–2025 Simon Chan；vendor头部包版本及C/H自报Tango 2.6.4 | 若复用vendor须随包保留MIT版权/许可及准确包清单。桌面不用WebUSB通信，不代表复用的其他Tango代码可省许可；不要从总称2.6.4推断每个vendor包同版 |
| xterm/fit | LICENSE-xterm.txt：xterm.js5.5.0，作者2017–2019、SourceLair2014–2016、Christopher Jeffrey2012–2013；FitAddon0.10.0作者2019；MIT | 锁定实际采用版本后随二进制/源码附完整声明；若改用npm新版，按该版声明重新核对，不把基线版本当初始化版本决定 |
| css/图标/favicon/内联产物 | css/styles.css带同源SPDX；index符号图形内嵌，assets/favicon.ico与docs/assets/favicon.ico未见独立许可声明；docs/index内嵌MIT第三方内容 | 正式视觉沿用本项目Mantine与产品图标，不默认复制favicon或作者品牌；若需采用单独资源，先确认权利范围并保留适用许可。内联HTML不能当成已去除第三方许可的纯GPL文件 |
| ADB/Fastboot与解锁依赖 | 不属于该网页自有源码许可可替代的对象；#15/#17固定采用版本后逐项核查 | 发布清单逐工具记录来源SHA/版本、依赖/原生库/驱动/loader、资源摘要与可再分发依据；不能随意附带厂商loader或PowerDumper APK。不可捆绑的资源明确合法获取/本地导入流程；当前没有已批准分发结论 |

发布交付方案：#31 为每个采用版本同批归档完整对应源码包、lockfiles、构建/打包脚本、复用变更及许可/版权/第三方清单，在成品关于页提供本版入口；GPL非源码形式分发选择与对应源码同地点提供的方式并核对GPL §6，涉及User Product时核对Installation Information义务。不以在线上游HEAD链接替代本版对应源码；MIT声明随包保留。**这是一份待执行安排：没有发布包、完整依赖清单、已批准最终许可或已完成合规验收。**

## 13. 本地任务回填与版本决策

下列是准备回填GitHub的本地映射；本次没有改远端issue、建子issue或标记完成。现有八个M5任务足以承接全部已识别项，暂不虚构新任务编号。

| 远端任务 | 待回填功能ID/补充重点 |
| --- | --- |
| #23 M5a | DEV-01–25；#19共享连接实现、#20模式任务；CPU可用频率与仅展示主板遮罩不能漏 |
| #24 M5b | APP-01–20；普通安装非split；split通过脚本可达；用户0/系统普通卸载自动-k/卸载锁SDK映射/完整ZIP/19包集合 |
| #25 M5c | FILE-01–07；此外父任务已要求的真实目录浏览与双向流式传输是桌面新增承诺，不冒称网页现有目录浏览功能 |
| #26 M5d | DSP-01–08、SYS-01–10、TUNE-01–05；特别是旋转提示/时钟秒数、默认删除设置、Wi-Fi含时区/NTP；SYS-10拆分待决 |
| #27 M5e | BAT-01–13；PowerDumper实际依赖路径/功率符号单位、采集上限及CSV格式 |
| #28 M5f | SCR-01–35；AUD-02/03；无通用BAT、无CALL子脚本；tcpip/usb实际可达，不丢失 |
| #29 M5g | TERM-01–08；AUD-02；Shell历史补全非收藏，不把activity缓冲误报持久任务日志 |
| #30 M5h | SET-01–18；AUD-03/04；FILE-07目录、脚本授权协作；五色主色冲突未决、遗留无效键不能上开关 |
| #19 / #14 | AUD-01潜在桥接接口、AUD-05范围核查；新增无线配对/mDNS方案未定版，不声称实现 |
| #16 | 固定本清单SHA；后续页面/动作/选项/解释器/资源与许可变化进入差异清单，新增ID沿用稳定编号不重排旧ID |
| #31 | 每个ID的承诺版本/平台实现提交与验收证据、GPL及第三方分发包；AUD-06语义修正可追溯 |

**分期结论**：全部基线ID保留在M5总目标；当前没有经确认的首版/后续划分或独立桌面版本号，故每项明确标“待决定”，不私自写v0.1.0。#14不得据这份盘点直接关闭：需维护者确认逐项发布批次、许可和主题/操作冲突后回填；#31部分版本只能关闭该版承诺项，不能关闭M5/#10。

验收字段回填格式：`功能ID / 桌面目标版本 / Windows实现提交、系统与主机工具、精确设备/ROM、操作输入/结果、证据路径 / macOS同字段 / 权限或ROM限制 / 未完成原因`。设备改动类还记录原值、写入值、读回/恢复（基线有恢复时）及实际效果；瞬时操作记录真实行为；脚本保留解释器对照夹具与错误行号；本地项验证路径拒绝、导入错误和跨启动持久化。任一平台缺证据保持未完成。

## 14. 盘点验证记录

- 临时只读研究clone，`git rev-parse HEAD` 返回上述完整SHA；未修改仓库third_party或上游源码。
- 对照模块与docs/index内联业务的文本差异，确认无另一套遗漏功能；检查全部页面、data-action/data-app/data-key/data-reboot/data-builder/data-setting与动态生成选项，以及脚本可达Service.execute分支。
- 负向核查覆盖无线调用点、收藏模型、刷写/投屏入口、静态服务器路由与遗留设置消费方；“未发现”限定本固定提交的产品源码，不断言所有未来版本都不存在。
- 纯文档变更，不运行构建/测试或真机操作。没有Windows/macOS客户端验收证据，没有工具采纳或发布操作。日期均为JST。
- 文档检查：149个功能ID和6个AUD ID无重复；四份交付文档相对链接均存在；`git diff --check`通过。源码对照的模块行数为H40/A162/C86/B49/T35/X84，引用对应固定提交。未修改远端issue，#14关闭所需的版本/产品/许可决策仍保持未完成。

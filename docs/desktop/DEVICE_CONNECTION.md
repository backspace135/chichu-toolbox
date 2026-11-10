# M1b 真实设备连接（#19）

更新：2026-11-10 JST。用户决定本次合并实现并关闭 #19，Windows 暂不验收。下文未通过的真机/安装项保持事实记录，不标记为已验证；相关验收继续由 M1 / #10 与发布验收 #31 跟踪。

## 固定分发输入

`apps/desktop/scripts/prepare_platform_tools.py` 下载 Google Platform-tools **36.0.2-14143358**，仅构建时联网；客户端运行不下载工具，不依赖 PATH。来源：

| 平台 | 固定来源 | ZIP SHA-256 | 实际工具架构 |
| --- | --- | --- | --- |
| macOS | `https://dl.google.com/android/repository/platform-tools_r36.0.2-darwin.zip` | `106a5d31fad8c1c0c5a180d06f5779767d129d7d5edbe629005c11a85eec5b4b` | universal x86_64 + arm64；原生 arm64 运行，无 Rosetta |
| Windows | `https://dl.google.com/android/repository/platform-tools_r36.0.2-win.zip` | `b024d4f319d6ad3004de1ba7b96a5c7c5f3512e8b14126308d598b4ab93dcead` | Google 原包 ADB/Fastboot/两 DLL 均为 x86 PE，Windows x64 使用 WOW64；主程序仍为 x64 |

工具原样放入 `src-tauri/resources/platform-tools/`（忽略目录）。使用 Tauri resources 映射到包内 `platform-tools/`，代替 externalBin，保留工具和 DLL 相邻布局。macOS 安装后路径为 App `Contents/Resources/platform-tools/`；Windows 为应用资源根下 `platform-tools/`。开发态仅用工程资源路径，发布态仅用 Tauri resource_dir。

随包包含 `NOTICE.txt`（各组件版权及 Apache/BSD/MIT 等完整声明）、`source.properties` 和 `manifest.json`（来源、目标、ZIP 及逐文件 SHA-256）。许可以实际 NOTICE 为准，不能把整个 SDK 当单一许可证。Google SDK 下载条款与正式发行安排仍需 #31 核对。macOS `file` 确认两工具包含 arm64；`otool -L` 仅系统库/框架，无 Homebrew 动态依赖。Windows 保留 `AdbWinApi.dll` / `AdbWinUsbApi.dll`，依赖与无开发环境运行尚待 Windows 验证。

## Server、权限与发现

- Rust 直接连接 `127.0.0.1:5037` 查询 `host:version` / `host:devices-l`，不调用可能自动替换旧 server 的 `adb devices`。协议必须为 `0029`（41）；冲突/非 ADB 端口响应明确报错，不调用 kill-server。协议版本不冒充 server 实际二进制版本。
- 现有兼容 server 按外部共享服务使用，所有权仍属外部工具。没有监听服务时启动随包 `adb -L tcp:127.0.0.1:5037 server nodaemon`，由本应用持有前台子进程；worker 退出释放该进程，不停止共享 server。现有 server 的精确版本/进程所有者未可靠取得，不猜测。自有 server 当前沿用 ADB 默认用户密钥位置，不清除或替换其他工具的密钥；专属凭据管理归 #30。
- 每 3 秒只读扫描，刷新按钮可唤醒；这是当前 ADB 与无事件接口的 Fastboot 的统一采样周期，不是失败自动重试。按真实 `device` / `unauthorized` / `offline` / `recovery` / `sideload` 等枚举状态显示。已观测到断开时清除当前选择，不自动选择第一台。
- ADB shell-v2 明确指定 **transport_id**，读取 getprop/id/uname；检查真实退出包，不把 stderr 当成功。字段记录来源和 Unix 毫秒时间，界面显示 JST，区分已读取、未提供、无权限、读取失败。没有执行 su、提权或安装 Android 客户端。
- Fastboot 枚举后每次 getvar 明确 `-s serial`；按退出码判断失败，成功数据可来自 stderr。只有 `is-userspace: yes/no` 才识别 Fastbootd/Bootloader Fastboot，未提供或未知值保留读取失败，不猜模式。无 EDL/BROM 支持声明。
- Fastboot 目标拔出时 CLI 会无限等待，单次只读 getvar 5 秒截止并结束进程；ADB shell 断连无退出包时同样 5 秒结束读取；整个 ADB 扫描 20 秒截止，避免非 ADB 程序占用 5037 时界面永久停留旧数据。错误可见，没有自动重启 server。

## 权威状态与身份

`devices.rs` 持有快照、当前选择和订阅者。订阅在同一锁内先发送快照再注册后续更新，选择与扫描也在该锁发布；前端仅消费 Channel，不单独合并旧设备的异步字段。取消订阅释放 Channel，离线结果不写入另一台设备。ADB ID 包含 transport，重连不会沿用旧 transport。

保留当前 serial、ADB transport/USB 路径、设备报告的 ro.serialno/Fastboot serialno、产品及各字段采集时点，供 #20 使用。这些是观测，不是已验证跨模式匹配。#20 必须按实际证据关联；没有唯一标识需人工确认，禁止依枚举顺序关联。选择不会触发模式切换或设备写入。

## 本次验证与未完成项

- macOS 本地固定 Rust 1.99.1、Node 24.22.2、npm 11.19.1：`npm run build`、`npm run lint`、`cargo clippy --locked --all-targets -- -D warnings` 通过；新增 `devices::tests::multiline_property_does_not_discard_device_build` 通过。修复真机 MIUI boot.reason.history 多行值导致 getprop 解析失败。
- 两平台下载脚本校验通过，Windows PE 架构在 Mac 静态核查，不能算 Windows 执行验收。
- macOS arm64 `tauri build --target aarch64-apple-darwin --bundles app,dmg -- --locked` 成功；产物位于 `apps/desktop/src-tauri/target/aarch64-apple-darwin/release/bundle/`。
- 正式 WKWebView App 实际观察到 cannon/M2007J22C、ADB shell uid=2000、内核；设备断开后列表 0、重连恢复，顶部选择展示同一设备。验证期间可用 USB 设备发生变化，不能据起始 CLI 两台设备枚举声称 App 多设备验收完成。
- 本地窗口截图：`apps/desktop/build/m1b-macos-device-before-getprop-fix.png`（修复前，真实展示型号/内核/Shell 及 getprop 错误；不能用作修复后完整字段通过证据）。CI 分流 6 项测试通过。
- **未完成**：修复后系统构建字段的正式窗口复验、多设备同时切换、未授权/offline、Fastboot/Fastbootd 真机、server 协议冲突/自有 server 启停集成验证；深浅色/最低系统/无开发环境安装、Windows 完整验收。g16win SSH 返回 `Connection closed by 120.55.93.189 port 10022`，未访问项目外路径或修改驱动。
- 尚未取得 Windows WebView2 离线安装器精确版本/摘要、最低四段版本与缺少 Runtime 环境证据；保留现有 offlineInstaller 配置，不能宣称 M1 全部完成。

本次按用户决定关闭实施 issue，不代表上述验收项全部通过，也不据此关闭父任务 M1。Windows、模式与安装验收缺口保留至后续验收。

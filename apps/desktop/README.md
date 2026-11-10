# 桌面客户端

Windows、macOS 共用的桌面工程入口。技术栈已确定为 **Tauri 2 + React + TypeScript + Rust**，前端构建使用 Vite。UI 采用 **Mantine + Tabler Icons**，沿用用户确认的交互预览与 [桌面端 UI 规范](../../docs/desktop/UI_DESIGN.md)。

- macOS 仅支持 Apple Silicon 原生 arm64，不提供 Intel 构建。
- Windows 目标暂按 x64 规划。
- 产品目标为自动识别/推荐已验证Bootloader强解方案，并保留gjgjxweb固定基线全部功能。
- 已接入随包 ADB/Fastboot、Rust 设备发现/信息采集与顶部设备选择（#19），以及原生任务记录、设备占用、截图保存、模式重启及任务中心（#20）。连接实现与双平台验收缺口见 [连接服务](../../docs/desktop/DEVICE_CONNECTION.md)。
- `0.0.0` 和 `com.chichu.toolbox.desktop.dev` 仅用于内部包，不是正式产品版本或发行身份。

参见 [桌面端技术栈决定](../../docs/desktop/TECH_STACK.md)、[UI 交互预览](../../docs/assets/desktop-mantine-mock.html)、[仓库布局](../../docs/REPOSITORY_LAYOUT.md)与 [项目总览](../../README.md)。

后续按 [详细开发文档](../../docs/desktop/DEVELOPMENT.md) 的 M0–M6 推进。M1=#18/#19，M2=#20，M3=#21，M4=#22，M5=#23–#30，M6=#31。M4 依赖 M0b+M2+M3，普通工程不等待 M0b。

## 开发与内部打包

使用 [平台基线](../../docs/desktop/PLATFORM_BASELINE.md) 固定的 Node `24.22.2` / npm `11.19.1`、Rust `1.99.1`。Rust 工具链由本目录 `rust-toolchain.toml` 选择；macOS 安装 Xcode/CLT，Windows 安装 MSVC C++ Build Tools 与 Windows SDK。以下命令在本目录运行：

```sh
npm ci
python3 scripts/prepare_platform_tools.py
npm run typecheck
npm run lint
npm run tauri dev
```

```sh
cargo fmt --manifest-path src-tauri/Cargo.toml --check
cargo clippy --manifest-path src-tauri/Cargo.toml --locked --all-targets -- -D warnings
cargo test --manifest-path src-tauri/Cargo.toml --locked

# Apple Silicon 本机；14.0 为评估编译下限，尚非支持承诺
MACOSX_DEPLOYMENT_TARGET=14.0 npm run tauri build -- --target aarch64-apple-darwin --bundles app,dmg -- --locked
# Windows 本机，包含 WebView2 离线安装器
npm run tauri build -- --target x86_64-pc-windows-msvc --bundles nsis -- --locked
```

`npm run dev` 仅启动前端：浏览器没有原生 IPC，会展示服务读取失败，不注入模拟设备。主题与紧凑模式保存在当前 WebView 的本地存储中。相关回归测试：`cargo test --manifest-path src-tauri/Cargo.toml --locked devices::tests`。

- `src/app/`：工作区、六入口、主题与 CSS Modules；`src/features/`：实际业务入口；`src/bridge/`：明确命名的 IPC。
- `src-tauri/src/`：单 crate 原生状态与命令；`capabilities/`、`permissions/` 限定主窗口的状态读取能力，不授权电脑 shell、任意文件或远程来源。
- `tasks.rs` 持有任务与设备占用；创建任务时固定设备/transport/操作/工具路径。`subscribe_tasks` 在同一锁内先发送完整快照再登记 Channel，后续快照带递增序号；切页与切设备不改变任务目标。终端、脚本与解锁接入时必须调用同一 `acquire` / `release` 原生契约，持有到进程/流实际退出；不向前端暴露任意主机命令。跨模式身份尚未解析时暂停其他设备会话创建，以免改号后的设备绕过占用。
- 截图由系统保存对话框选址，经固定 ADB transport 流式写入任务目录、检查 PNG 头/尺寸/结束块后保存目标文件；保存失败保留原始截图。模式重启仅发送固定枚举命令，退出后保留占用，需刷新设备并在任务中心验证；只接受命令结束后的新观测、目标模式和唯一序列号关联，否则要求人工确认。取消/中断和设备状态验证分开，未自动续写或重试。
- 每任务在平台 `app_local_data_dir/tasks/<id>/` 保存独立摘要、stdout/stderr 与原始截图；记录退出码、原因、随包工具版本和产物路径/大小。界面展示 JST。诊断导出由系统对话框选择位置，默认替换设备标识，可显式包含；导出说明列明内容，截图二进制不进入文本报告。
- 窗口关闭/应用退出有活动任务或会话占用时阻止关闭，提示到任务中心结束或验证。macOS 独立原生监护进程用管道 EOF 清理专属进程组；Windows 使用 kill-on-close Job Object，主动取消另清理进程树。重新启动将非终态记录标为中断、设备状态未验证，不自动恢复命令。Windows 进程 Job 绑定/退出语义尚待本机实际验收。
- 图标源为 `docs/assets/app-icon.svg`；使用固定 Tauri CLI 的 `npm run tauri icon ../../docs/assets/app-icon.svg` 生成桌面图标，非桌面图标忽略。
- `package-lock.json` 与 `src-tauri/Cargo.lock` 两平台共用；CI 用 `npm ci` 和 `--locked`。新增静态检查器固定 `oxlint=1.93.0`（支持当前 TS 语法），序列化依赖固定 `serde=1.0.228`。
- 安装包位于 `src-tauri/target/<target>/release/bundle/`。CI 按完整 PR diff（含删除/重命名）分流：文档/Android 跳过桌面检查；UI 仅 Linux lint/类型/生产构建；共享 Rust 执行双平台 `clippy --all-targets`；IPC bridge 检查两端；Cargo、打包/权限/资源、工具链及构建配置变更执行原生检查。工程外图标变更纳入。PR 和 main push 均不生成 release 安装包，仅手动触发打包。
- 原生 CI 执行设备解析回归测试；安装器、release 链接和无开发环境运行由手动打包/里程碑验收覆盖。
- 前端生产构建只执行一次，原生 job 下载同次运行的 dist，打包覆盖 beforeBuildCommand 避免重建。仅原生检查需要时上传 dist（保留一天）；npm 下载和 Rust 编译依赖缓存复用。只监听 PR 与 main push，同 PR 新提交取消旧任务，原生矩阵失败后停止其他平台。`desktop-ci` 检查所选 job 必须成功，允许按规则跳过；main 同样按 diff 分流，不无条件全跑。不使用生产签名凭据。
- 构建前准备固定 Platform-tools 36.0.2，校验 ZIP SHA-256、架构并随包包含工具、Windows DLL、NOTICE、版本文件与逐文件摘要。直接执行 Cargo 检查前也需准备资源；Tauri dev/build 自动准备，CI 显式准备。不从 PATH 查找运行时工具。解锁运行时仍由 #17 / #22 接入。

2026-11-10（JST）本地验证：macOS 26.6.2 arm64、Xcode 27.2 / SDK 27.2；固定 Node/npm/Rust 完成前端检查、Rust clippy 和 arm64 App/DMG 构建。`g16win` SSH 连接被关闭，未执行 Windows 构建。正式窗口深浅色、缩放、最小尺寸、焦点/导航，以及两端无开发环境安装尚未验收；最低 WebView2 四段版本仍待 Windows 首次运行核实。#18 与 M1 不据此标记全部完成。

#20 本轮开发验证（2026-11-10 JST）：固定 Rust 1.99.1 下 `cargo check --manifest-path src-tauri/Cargo.toml --locked`、`cargo build --manifest-path src-tauri/Cargo.toml --locked` 通过；固定 Node 24.22.2 下 `npm run build`（含 TS 检查）通过。受控 `/bin/sleep` 独立进程组 + `--task-process-watch` 的管道 EOF 检查通过，确认进程组以 SIGKILL 退出。`g16win` SSH 被关闭，Windows 未编译/运行。两端正式窗口的截图保存、模式切换、切页/设备、断连、保存失败、取消/关闭/异常恢复和多设备错配尚未完成针对性验收，#20 保持未完成；当前开发不执行真机重启。

[全功能与复用清单](../../docs/desktop/FEATURE_INVENTORY.md) 固定 `CzJam/gjgjxweb@f84f9d50cf5ba63928192e64776081fd1cd9ea60`，逐项记录源码/输入/权限ROM、六入口、原生接口、许可和M5归属。全部功能保持跟踪，首版/后续批次待确认，部分发布不能关闭M5或总目标；Windows/macOS状态均未实现、未验收。上游1.0.0不是本客户端版本。

复用业务/解析/BAT逻辑须落实GPL-3.0-only对应源码及第三方MIT声明；最终发布许可待确认。原生层负责设备/进程/文件/长任务，终端与脚本参与同设备占用；浏览器目录授权和密钥清理按桌面等价实现，只处理本应用拥有的数据。无线配对/收藏不能据未接线函数或依赖能力宣称已有支持。

每日跟踪（#16）区分观测/候选/采用，计划09:00 JST，不在客户端在线替换工具；每版冻结验收SHA。两端正式Tauri窗口、真实设备与无开发环境安装分别验收，截图和编译不能代替强解证据。

整体进度见 [桌面端总 issue #10](https://github.com/backspace135/chichu-toolbox/issues/10)。

M0b 的调查运行时构建/校验入口、M3/M4 条件契约和实际测试结果见 [MTK_RUNTIME.md](../../docs/desktop/MTK_RUNTIME.md)。macOS arm64 隔离启动已验证；Windows 与双平台锁定→解锁真机验收仍阻塞，尚无正式采用方案。

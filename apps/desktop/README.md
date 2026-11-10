# 桌面客户端

Windows、macOS 共用的桌面工程入口。技术栈已确定为 **Tauri 2 + React + TypeScript + Rust**，前端构建使用 Vite。UI 采用 **Mantine + Tabler Icons**，沿用用户确认的交互预览与 [桌面端 UI 规范](../../docs/desktop/UI_DESIGN.md)。

- macOS 仅支持 Apple Silicon 原生 arm64，不提供 Intel 构建。
- Windows 目标暂按 x64 规划。
- 产品目标为自动识别/推荐已验证Bootloader强解方案，并保留gjgjxweb固定基线全部功能。
- 已初始化共享 Tauri 工作区、六入口、外观偏好、工具搜索、任务抽屉与原生状态 IPC。设备连接（#19）、任务执行（#20）及各业务能力尚未接入，客户端明确展示未实现状态。
- `0.0.0` 和 `com.chichu.toolbox.desktop.dev` 仅用于内部包，不是正式产品版本或发行身份。

参见 [桌面端技术栈决定](../../docs/desktop/TECH_STACK.md)、[UI 交互预览](../../docs/assets/desktop-mantine-mock.html)、[仓库布局](../../docs/REPOSITORY_LAYOUT.md)与 [项目总览](../../README.md)。

后续按 [详细开发文档](../../docs/desktop/DEVELOPMENT.md) 的 M0–M6 推进。M1=#18/#19，M2=#20，M3=#21，M4=#22，M5=#23–#30，M6=#31。M4 依赖 M0b+M2+M3，普通工程不等待 M0b。

## 开发与内部打包

使用 [平台基线](../../docs/desktop/PLATFORM_BASELINE.md) 固定的 Node `24.22.2` / npm `11.19.1`、Rust `1.99.1`。Rust 工具链由本目录 `rust-toolchain.toml` 选择；macOS 安装 Xcode/CLT，Windows 安装 MSVC C++ Build Tools 与 Windows SDK。以下命令在本目录运行：

```sh
npm ci
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

`npm run dev` 仅启动前端：浏览器没有原生 IPC，会展示服务读取失败，不注入模拟设备。主题与紧凑模式保存在当前 WebView 的本地存储中。没有新增逻辑测试套件，`cargo test` 当前仅执行编译/测试框架检查。

- `src/app/`：工作区、六入口、主题与 CSS Modules；`src/features/`：实际业务入口；`src/bridge/`：明确命名的 IPC。
- `src-tauri/src/`：单 crate 原生状态与命令；`capabilities/`、`permissions/` 限定主窗口的状态读取能力，不授权电脑 shell、任意文件或远程来源。
- 图标源为 `docs/assets/app-icon.svg`；使用固定 Tauri CLI 的 `npm run tauri icon ../../docs/assets/app-icon.svg` 生成桌面图标，非桌面图标忽略。
- `package-lock.json` 与 `src-tauri/Cargo.lock` 两平台共用；CI 用 `npm ci` 和 `--locked`。新增静态检查器固定 `oxlint=1.93.0`（支持当前 TS 语法），序列化依赖固定 `serde=1.0.228`。
- 安装包位于 `src-tauri/target/<target>/release/bundle/`。CI 按完整 PR diff（含删除/重命名）分流：文档/Android 跳过桌面检查；UI 仅 Linux lint/类型/生产构建；共享 Rust 执行双平台 `clippy --all-targets`；IPC bridge 检查两端；Cargo、打包/权限/资源、工具链及构建配置变更执行原生检查。工程外图标变更纳入。PR 和 main push 均不生成 release 安装包，仅手动触发打包。
- 当前没有 Rust 测试用例，不链接空测试二进制；`clippy --all-targets` 覆盖测试目标的编译检查。后续引入业务测试时须同步添加相关测试执行，不能用 clippy 代替运行测试。安装器、release 链接和无开发环境运行由手动打包/里程碑验收覆盖。
- 前端生产构建只执行一次，原生 job 下载同次运行的 dist，打包覆盖 beforeBuildCommand 避免重建。仅原生检查需要时上传 dist（保留一天）；npm 下载和 Rust 编译依赖缓存复用。只监听 PR 与 main push，同 PR 新提交取消旧任务，原生矩阵失败后停止其他平台。`desktop-ci` 检查所选 job 必须成功，允许按规则跳过；main 同样按 diff 分流，不无条件全跑。不使用生产签名凭据。
- 当前不捆绑不存在的 ADB/Fastboot 或解锁运行时；对应产物由 #19 / #17 接入。

2026-11-10（JST）本地验证：macOS 26.6.2 arm64、Xcode 27.2 / SDK 27.2；固定 Node/npm/Rust 完成前端检查、Rust clippy 和 arm64 App/DMG 构建。`g16win` SSH 连接被关闭，未执行 Windows 构建。正式窗口深浅色、缩放、最小尺寸、焦点/导航，以及两端无开发环境安装尚未验收；最低 WebView2 四段版本仍待 Windows 首次运行核实。#18 与 M1 不据此标记全部完成。

[全功能与复用清单](../../docs/desktop/FEATURE_INVENTORY.md) 固定 `CzJam/gjgjxweb@f84f9d50cf5ba63928192e64776081fd1cd9ea60`，逐项记录源码/输入/权限ROM、六入口、原生接口、许可和M5归属。全部功能保持跟踪，首版/后续批次待确认，部分发布不能关闭M5或总目标；Windows/macOS状态均未实现、未验收。上游1.0.0不是本客户端版本。

复用业务/解析/BAT逻辑须落实GPL-3.0-only对应源码及第三方MIT声明；最终发布许可待确认。原生层负责设备/进程/文件/长任务，终端与脚本参与同设备占用；浏览器目录授权和密钥清理按桌面等价实现，只处理本应用拥有的数据。无线配对/收藏不能据未接线函数或依赖能力宣称已有支持。

每日跟踪（#16）区分观测/候选/采用，计划09:00 JST，不在客户端在线替换工具；每版冻结验收SHA。两端正式Tauri窗口、真实设备与无开发环境安装分别验收，截图和编译不能代替强解证据。

整体进度见 [桌面端总 issue #10](https://github.com/backspace135/chichu-toolbox/issues/10)。

M0b 的调查运行时构建/校验入口、M3/M4 条件契约和实际测试结果见 [MTK_RUNTIME.md](../../docs/desktop/MTK_RUNTIME.md)。macOS arm64 隔离启动已验证；Windows 与双平台锁定→解锁真机验收仍阻塞，尚无正式采用方案。

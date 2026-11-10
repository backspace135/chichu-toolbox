# 桌面平台与 M1 初始化基线

**记录日期：2026-11-10（JST）；对应 #15 / 父任务 [#10](https://github.com/backspace135/chichu-toolbox/issues/10)。**

这是 #18 / #19 的决策输入，不是已发布支持声明。框架和 Mantine UI 的用户确认沿用 [TECH_STACK.md](TECH_STACK.md) 与 [UI_DESIGN.md](UI_DESIGN.md)；本文新增的工程参数是实施选择，产品最低 OS 尚未获确认。#15 已随 PR #33 关闭，未决项继续由 #10/#18/#19/#31 跟踪。#18 共享工程与锁文件已在本地落地，首次结果见下文。

## 1. 平台状态与最低系统依据

| 项目 | M1 可直接采用的工程输入 | 产品确认 / 真实支持状态 |
| --- | --- | --- |
| Windows | x64；`x86_64-pc-windows-msvc`；在 Windows 原生构建 | 本次任务指定 x64 工程范围；父 issue 的正式支持声明仍待确认；无本项目安装/运行证据 |
| macOS | Apple Silicon；`aarch64-apple-darwin`；在 arm64 Mac 原生构建 | 架构用户已确认；无正式 Tauri 安装/运行证据；不提供 Intel/universal，不依赖 Rosetta |
| 较低最低 OS 候选 | Windows 10 22H2（build 19045）x64 / macOS 14.0 arm64 | 仅候选，未确认、未验证 |
| 较高最低 OS 候选 | Windows 11 x64 / macOS 15.0 arm64 | 仅候选，Windows 11 具体版本/最低 build 也未定 |
| M1 暂定编译/打包下限 | macOS `MACOSX_DEPLOYMENT_TARGET=14.0` 且 `bundle.macOS.minimumSystemVersion="14.0"`；Windows 以较低候选作后续测试矩阵输入 | 保留较低候选的评估可能，不等于承诺支持；不得在下载页写“支持 Windows 10/macOS 14” |

### 实际资料给出的下限，不等于成品下限

- [Tauri 前置依赖](https://v2.tauri.app/start/prerequisites/)列出 macOS 10.15+、Windows 7+，但这些通用文档不能代表本项目所选 Rust、WebView、UI 和工具的交集，不能据此承诺旧系统支持。
- [Rust 1.99.1 的 Apple target 文档](https://github.com/rust-lang/rust/blob/1.99.1/src/doc/rustc/src/platform-support/apple-darwin.md)给出 arm64 macOS 11.0 下限，并说明 `MACOSX_DEPLOYMENT_TARGET`；本项目候选 14/15 高于该下限。Tauri `2.12.5` 与 tauri-build `2.7.4` 的发布 crate `Cargo.toml` 均声明 MSRV `1.90`，Tokio `1.54.1` 声明 `1.85`；选用 Rust `1.99.1` 满足直接依赖，传递依赖仍需首次 Cargo.lock 解析/构建证明。
- [Node 24.22.2 BUILDING.md](https://github.com/nodejs/node/blob/v24.22.2/BUILDING.md)给出预构建 Node 的 Windows x64 `>= Windows 10/Server 2016`、macOS arm64 `>=13.5`。这是**开发工具**要求，Node 不随 UI 主程序分发，不用它反推产品运行下限。
- [Mantine 9.8.0 浏览器支持](https://mantine.dev/browser-support/)列出 Chromium 108+、Safari 15.4+，其 `:where` / `@layer` 基础样式要求与可选组件特性需分别核查。Safari 版本不直接等于系统 WKWebView；[Tauri WebView 对照](https://v2.tauri.app/reference/webview-versions/)说明 WKWebView 随系统更新，并将 macOS 14.0 beta 对照到 Safari 17.0。此表是候选评估依据，正式包须记录实际 OS build、WebKit 版本与行为。
- Vite `8.3.7` / React 插件 `6.3.0` 的 registry `engines.node` 为 `^20.19.0 || >=22.12.0`；[Vite 指南](https://vite.dev/guide/)区分开发态现代浏览器要求和生产构建 target。M1 设置生产 `build.target=['edge108','safari17']`、`build.cssTarget=['edge108','safari17']`，匹配较低候选的评估基线；不靠 Vite 默认 target 推定 WKWebView 可用，也不因此加入无依据 polyfill。开发 HMR 在实际开发机验证，最低系统主要验证生产包。
- [Microsoft 官方维护政策](https://learn.microsoft.com/en-us/deployedge/microsoft-edge-supported-operating-systems)目前说明 Windows 10 22H2 的 Edge/WebView2 更新至少持续至 **2028 年 10 月**，不要求加入 ESU；这不是 Windows OS 本身仍获同等支持，也不是本应用已验证支持。Windows 11 是否缩小维护矩阵是产品选择，不能由此资料代替决定。

**实际成品交集** = 主程序 + 安装器 + WebView/UI + ADB/Fastboot + 所选解锁运行时/原生库 + USB 驱动/权限 + 签名/公证。M0b 如发现更高要求，附证据回到 #15 更新候选，不静默删除某平台核心功能。

## 2. 精确工具链与锁策略

下表是查询后固定的 M1 输入，版本不随 `latest` 移动。#18 已使用固定版本生成 npm/Cargo 锁文件并完成本机 macOS arm64 构建，尚未完成双平台运行/安装验证。补充直接依赖为 serde `1.0.228`、Oxlint `1.93.0`；锁文件见 `apps/desktop/`。

| 类别 | 精确版本 / 包名 | 获取与兼容依据 |
| --- | --- | --- |
| Node.js | `24.22.2`（Krypton LTS） | [官方分发索引](https://nodejs.org/dist/index.json)及 [该版目录](https://nodejs.org/dist/v24.22.2/)包含 win-x64 与 darwin-arm64，随附 npm `11.19.1`；取官方 `SHASUMS256.txt` 校验 |
| npm | `11.19.1` | 沿用上述 Node 随附版本；[版本元数据](https://registry.npmjs.org/npm/11.19.1)要求 Node `^20.17.0 || >=22.9.0` |
| Rust / Cargo | Rust toolchain `1.99.1`，使用该工具链随附 Cargo | [固定分发清单](https://static.rust-lang.org/dist/channel-rust-1.99.1.toml)已确认两个目标 `available=true`；不单独升级 Cargo |
| Tauri Rust | `tauri = "=2.12.5"`；build dependency `tauri-build = "=2.7.4"` | [tauri](https://crates.io/api/v1/crates/tauri/2.12.5)、[tauri-build](https://crates.io/api/v1/crates/tauri-build/2.7.4)；已读取发布 tarball 中的 MSRV |
| Tauri JS / CLI | `@tauri-apps/api` `2.12.4`；`@tauri-apps/cli` `2.12.4` | [API](https://registry.npmjs.org/@tauri-apps/api/2.12.4)、[CLI](https://registry.npmjs.org/@tauri-apps/cli/2.12.4)；同属 2.x，Rust/API/CLI 独立发版，不强行凑相同补丁号 |
| Vite | `vite` `8.3.7`；`@vitejs/plugin-react` `6.3.0` | [Vite](https://registry.npmjs.org/vite/8.3.7)、[插件](https://registry.npmjs.org/@vitejs/plugin-react/6.3.0)；插件 peer Vite `^8.0.0`；OXC/Babel/React Compiler 的 peer 标记 optional，M1 不额外启用 React Compiler |
| React | `react` / `react-dom` 均 `19.3.1` | [React](https://registry.npmjs.org/react/19.3.1)、[DOM](https://registry.npmjs.org/react-dom/19.3.1)；DOM peer React `^19.3.1` |
| TypeScript | `typescript` `7.0.2` | [元数据](https://registry.npmjs.org/typescript/7.0.2)；Node `>=16.20.0` |
| React 类型 | `@types/react` / `@types/react-dom` 均 `19.3.0` | [React 类型](https://registry.npmjs.org/@types/react/19.3.0)、[DOM 类型](https://registry.npmjs.org/@types/react-dom/19.3.0)；后者 peer 前者 `^19.3.0` |
| Node 类型 | `@types/node` `24.10.1` | [元数据](https://registry.npmjs.org/@types/node/24.10.1)；固定 24.x 类型，不将类型补丁号误作 Node runtime 补丁号 |
| Mantine | `@mantine/core` / `@mantine/hooks` 均 `9.8.0` | [core](https://registry.npmjs.org/@mantine/core/9.8.0)、[hooks](https://registry.npmjs.org/@mantine/hooks/9.8.0)；core peer hooks **精确** `9.8.0`、React/DOM `^19.2.0`，已选 React 满足 |
| 图标 | `@tabler/icons-react` `3.51.0` | [元数据](https://registry.npmjs.org/@tabler/icons-react/3.51.0)；peer React `>=16` |
| PostCSS | `postcss` `8.5.32`；`postcss-preset-mantine` `1.18.0`；`postcss-simple-vars` `7.0.1` | [PostCSS](https://registry.npmjs.org/postcss/8.5.32)、[preset](https://registry.npmjs.org/postcss-preset-mantine/1.18.0)、[vars](https://registry.npmjs.org/postcss-simple-vars/7.0.1)；preset peer `>=8.0.0`，vars peer `^8.2.1`；按 [Mantine 安装说明](https://mantine.dev/getting-started/)接入 |
| 异步进程 | `tokio = "=1.54.1"` | [发布信息](https://crates.io/api/v1/crates/tokio/1.54.1)；按实际服务使用 `process`、`io-util`、`sync` 等 features，不默认 `full` |

### #18 应落地的文件与规则

1. `apps/desktop/.node-version` 写 `24.22.2`；`package.json` 中 `engines` 固定 Node `24.22.2`、npm `11.19.1`，`packageManager` 为 `npm@11.19.1`；项目 `.npmrc` 使用 `save-exact=true`、`engine-strict=true`。CI 明确检查 `node --version` / `npm --version`，`packageManager` 本身不是强制执行器。
2. 所有直接 npm 依赖使用表中精确值，不加 `^` / `~`；提交 npm 11 生成的 `package-lock.json`（lockfileVersion 3），两平台共用一个锁文件；CI 使用 `npm ci`，不使用 `--force` / `--legacy-peer-deps` 掩盖冲突，不并存 pnpm/yarn 锁文件。
3. `apps/desktop/rust-toolchain.toml` 固定 `channel="1.99.1"`、`profile="minimal"`、components `rustfmt` / `clippy`；各平台 job 安装本机对应 target，不在同一主机假定可以链接另一平台。
4. `src-tauri/Cargo.toml` 直接依赖用上表 `=版本`；提交应用的 `Cargo.lock`，Cargo 检查/构建使用 `--locked`，Tauri 打包前同样验证锁文件且不允许构建静默改锁。传递依赖版本、crate 校验值由 Cargo.lock 固定。
5. 使用项目本地 Tauri CLI（npm script 调用 `tauri`），不依赖全局 CLI，不用浮动 `create-tauri-app@latest` / `create-vite@latest` 重新选择版本。首次生成后检查 manifest 与此表一致。
6. 按功能增加 dialog、shell、终端、测试等依赖时，在对应任务固定可获取版本并同时更新清单与锁文件；M1 不预装全部插件。Rust 可直接管理工具进程，sidecar 打包本身不要求向前端开放 shell 插件。
7. 依赖升级通过独立变更更新版本表、两个锁文件及受影响平台验证；#16 观测到上游更新不自动更改此基线。工具二进制、Python wheel、WebView2 安装器另存版本/来源/架构/SHA-256/许可清单，不能靠 npm/Cargo 锁文件代替。

## 3. 窗口初始化参数

| 参数 | M1 输入 | 状态 / 依据 |
| --- | --- | --- |
| 主窗口 | label `main`；title `彳亍工具箱`；可缩放、保留系统窗口装饰 | 工程选择；平台菜单/快捷键遵循宿主习惯，不新增自绘标题栏 |
| 初始内容尺寸 | `width=1360`、`height=900`（逻辑像素） | 1360 宽沿用 mock 检查基准；900 高是初始化选择，非已验收尺寸；小屏须按工作区可用尺寸调整 |
| 最小内容尺寸 | `minWidth=980`、`minHeight=640`（逻辑像素） | 本次工程基线，尚待正式窗口验证；980 对齐 UI 规范左右分栏重排断点，640 高用于滚动工作区，不承诺 390px 正式窗口 |
| 导航 / 主操作 | 六入口、顶部设备上下文、任务抽屉及既有分步流程 | 用户已确认，详见 UI 规范 §5–7；本基线不改变导航、层级、主色或主操作 |

保留规范中的窄布局规则；最小窗口不替代缩放/响应式。宽表格只在容器内横向滚动，内容可纵向滚动，弹窗/抽屉焦点与主要操作可达。#18 在两端正式窗口分别验证初始/最小尺寸、系统缩放、深浅色；若需改变主操作或导航，按 UI 规范走确认流程。当前参数尚无视觉验收证据。

## 4. Sidecar 与安装包契约

### 路径与进程

- #19 准备 `src-tauri/binaries/adb-<target>[.exe]`、`fastboot-<target>[.exe]`；例如 `adb-x86_64-pc-windows-msvc.exe` / `adb-aarch64-apple-darwin`。`bundle.externalBin` 使用 `binaries/adb` / `binaries/fastboot`，不含 target 后缀；target 来自构建目标而非开发机猜测，见 [Tauri sidecar 规则](https://v2.tauri.app/develop/sidecar/)。
- 不在 #18 放不存在的工具路径使骨架打包失败；#19 有真实固定产物后接入 externalBin。Rust 解析安装后工具绝对路径并持有进程，不通过 PATH 找用户工具，也不把设备命令交给电脑 shell。前端仅调用明确业务 IPC。
- Windows 的 `AdbWinApi.dll` / `AdbWinUsbApi.dll` 等以实际产物依赖为准，保持 DLL 与其可执行文件所需相对布局；用 `bundle.resources` 或打包步骤明确映射，不能认为 externalBin 自动携带 DLL。检查 VC runtime 等依赖，若实际需要再选可再分发运行时交付，不要求用户安装 Visual Studio。
- macOS 工具、Python、扩展 `.so`、`.dylib` 全部必须含原生 arm64，部署下限不高于候选下限；检查 `file` / `lipo -info`、`otool -L` 和 load commands。仅依赖系统库或包内 `@rpath` / `@loader_path` / `@executable_path` 可解析位置，不留 `/opt/homebrew` 或开发机绝对路径；修正依赖路径后再签名。
- 工具清单记录上游 commit/tag、二进制版本、来源、架构、SHA-256、许可证/对应源码义务、所需动态库和实际测试 OS。ADB server 归属、版本冲突处理由 #19 固定，不无提示杀掉其他工具的 server。

### 平台交付参数

| 平台 | M1 工程选择 | 后续证明 |
| --- | --- | --- |
| Windows | 首先产 NSIS x64 `-setup.exe`，不用 `targets=all`；`webviewInstallMode.type="offlineInstaller"`，交付 Evergreen WebView2 离线安装器 | [Tauri 安装器说明](https://v2.tauri.app/distribute/windows-installer/)支持此模式；构建时仍需获取安装器，**用户安装时**不需联网补 WebView2。#19/#31 记录实际安装器版本/摘要与安装后 Runtime 版本，验证缺失、已有较旧/较新版本、离线与权限失败情形；不使用 `skip` 假定系统一定预装 |
| macOS | `app` / `dmg`，仅 arm64，使用系统 WKWebView | 无独立 Chromium/WebKit 包；#31 验证下载隔离属性、Gatekeeper、挂载/复制到 Applications、离开开发环境后启动 |

Evergreen 会更新，不假装运行时永久等于构建时版本；安装包输入与测试时的实际 Runtime 分别记录。M1 的 UI 评估下限为 Chromium 108，最终 `minimumWebview2Version` 还须覆盖实际 Rust WebView 依赖/API 的要求，由 #18/#19 在首次锁文件与 Windows 运行验证后固定**精确四段版本**；此项在首次 Windows 包验收前补齐，不拿文档示例版本充当已验证值。不选择 fixed runtime，避免无依据承担随应用补 WebView 漏洞的维护路线。

## 5. 签名、公证与解锁运行时交集

| 责任任务 | 必须交付 |
| --- | --- |
| #18 M1a | 分平台配置和无生产凭据构建路径；记录实际 Xcode/SDK、MSVC/Windows SDK、Tauri CLI 版本及 CI runner 镜像版本。Windows 需要 C++ Desktop Build Tools/MSVC 与 SDK；macOS 桌面可使用 Xcode/CLT。当前未检查 Windows 工具链，不虚构已安装版本 |
| #19 M1b | ADB/Fastboot 资源、动态库、安装器配置、许可与真实连接证据；内部未签名包清楚标注用途，不等同正式发行包 |
| #17 M0b | 冻结首条路线、设备/固件与工具资源；Python/原生依赖双平台最小可分发运行时；驱动、USB/主机授权、可分发资源及许可条件；分别提供从锁定到解锁的证据 |
| #31 M6 / 维护者 | 确认应用 identifier、产品版本和发行身份；受保护发布环境管理 Windows 签名身份/证书或签名服务、时间戳，以及 Apple Developer ID / Team ID、公证凭据；完成正式签名、公证和安装验收 |

macOS 采用 Developer ID 分发时，按 [Tauri 签名/公证指南](https://v2.tauri.app/distribute/sign/macos/)准备 hardened runtime；先签包内实际嵌套 Mach-O/动态库/可执行工具，再签外层 App，提交 notarization 并 staple，记录 `codesign --verify`、`spctl`、`stapler validate` 结果。Python/native runtime 的 entitlement 按真实打包技术决定，不预先开放宽泛例外。Windows 按 [签名指南](https://v2.tauri.app/distribute/sign/windows/)对自有 PE/安装器签名并时间戳，第三方产物保留其签名与许可，检查最终签名状态。生产凭据不进入仓库、普通 PR、上游候选检查或连接真机的普通 runner。

运行时交付不得要求用户安装 Node/Rust/Python/pip/Homebrew。#17 必须证明：精确 Python 解释器及 wheel/原生库在两个目标架构可获取、包内动态加载完整、无网络安装行为、脱离 Homebrew/Rosetta 能工作；是否需要 macFUSE 由实际 CLI 路线决定，不预装。Windows 驱动不因工具随包就自动存在，也不将驱动安装当作普通工具链修改；需记录签名、授权和具体部署流程。`mtkclient` 调查快照不等于已选定运行时，本文不固定尚无可分发证据的 Python/USB 库组合，也不提供利用或写入步骤。

M1 主程序初始化、普通设备功能与 M0b 可并行；首条解锁集成仍依赖 M0b 的运行时交集，不能用主程序启动证明解锁工具支持同一最低系统。

## 6. 本次实际验证记录

| 检查 | 实际结果 |
| --- | --- |
| GitHub 只读 | 已读取 `gh issue view 15` / `10 --repo backspace135/chichu-toolbox`；使用审订正文，未写远端 |
| 发布源 | 已通过 HTTPS 读取 npm 表列版本元数据（含 tarball/integrity、engines/peerDependencies）、Node 官方索引、Rust `1.99.1` manifest；读取 tauri / tauri-build / tokio 发布 crate 的 Cargo.toml。证明发布源有记录，不等于已安装/共同解析或构建成功 |
| 本机 OS / 架构 | `sw_vers`：macOS `26.6.2`（`25G83`）；`uname -m`：`arm64` |
| 本机 Node / npm | `node --version`：`v26.5.0`；`npm --version`：`11.17.0`；均与本文固定输入不同，未据此声称已验证目标工具链 |
| 本机 Rust | 当前 shell `rustc --version` 返回 command not found；同一串行命令随后 cargo/rustup 未执行，未断言整机没有其他位置的 Rust |
| 本机 Apple 工具链 | `xcodebuild -version`：Xcode `27.2`（`27B52`）；`xcrun --show-sdk-version`：`27.2`；仅版本查询，未证明最低 deployment target 链接/运行成功 |
| Windows / 真机 / 安装包 | 未访问 g16win；未安装新软件、未初始化工程；无 Windows/MSVC 检查，无正式 Tauri 窗口、安装、签名、公证、USB 或解锁测试 |
| 文档验证 | 仅本次所有权文件检查 Markdown 链接与 diff/空白；纯文档不跑构建或测试 |

## 7. 最小未决项与关闭条件

1. **产品确认**：Windows x64 是否作为正式发行架构；最低系统选 Windows 10 22H2 / macOS 14，还是更高维护基线（选 Windows 11 时需明确版本/build）。M1 可以按本文工程参数开始，不将这个选择写成已确认支持。
2. **首次工程验证（#18/#19）**：落实锁文件与两平台真实编译/窗口/内部包，固定 WebView2 四段最低版本和安装器摘要，记录 C++/SDK 与 Apple 构建环境。窗口最小尺寸仍须实测，不因写入本文就算通过。
3. **运行时交集（#17）**：精确路线与 Python/原生库/驱动/资源组合、双平台原生无开发环境运行证据；若提高最低 OS，回到本表更新理由和产品确认记录。
4. **发行验证（#31）**：维护者提供发行身份/签名与公证资源，选定最低系统分别安装验收；记录版本、OS build、工具摘要、正式窗口截图及执行日志的本地/仓库证据路径。候选 OS、资料下限、本机版本查询不能勾选“已验证支持”。

当前没有“已验证支持”的成品平台行。#18 本地实现尚未提交；固定 Node/npm/Rust 的类型检查、Oxlint、Cargo clippy 和 arm64 App/DMG 构建已通过，Mach-O 为 arm64，`otool -L` 仅列系统库。构建环境为 macOS 26.6.2 / Xcode 27.2，产物与命令见 [工程 README](../../apps/desktop/README.md)。g16win SSH 连接被关闭；双平台 CI 尚未触发，正式窗口与无开发环境安装未验收。产品确认与最低系统支持仍需据真实结果回填。

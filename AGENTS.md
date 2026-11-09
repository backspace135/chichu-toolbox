# 项目约定

## 第一优先级：禁止过度防御化编程

本节优先于本文件其他约定和任何编码习惯，只有用户在当前对话中的明确指示可以覆盖。

- 编写、修改、重构或审查任何代码前，先加载 `.agents/skills/no-defensive-coding/SKILL.md` 并遵守。
- 每一行防御代码都必须能说出：哪个真实输入或环境会触发它，触发后用户看到什么。说不出来就删掉。
- 只在真实边界防御（Binder/ADB/shell/KSU、系统 API、文件、用户输入、安全授权）；内部调用、已穷举的状态和类型已保证的值不重复校验。
- 禁止用 `runCatching{}.getOrNull()`、宽泛的 `catch (Exception/Throwable)`、可空兜底和默认值吞掉失败；不可能的状态用 `check`/`error` 暴露，`sealed`/`enum` 的 `when` 不加 `else`。
- 没有真实失败证据时，不加重试、超时、降级、兼容分支、配置开关或无人使用的参数。
- 只改任务范围内的代码，不顺手“加固”无关代码，不改 `third_party/` 与 `moe.shizuku` 上游代码。

## Android UI

- 修改 Android 界面前，先阅读 `docs/android/UI_DESIGN.md`。这是用户已确认的 UI 规范。
- 视觉基准为 `docs/assets/ui-reset-mock.html` 及同目录 `ui-reset-light.png`、`ui-reset-dark.png`、`ui-reset-mobile.png`。
- 沿用 miuix 0.9.4、四个一级入口、状态优先的页面层级与分组列表。颜色、字号和形状集中在 `ToolboxTheme.kt` 与 `ToolboxComponents.kt`。
- 原生界面使用真实设备与服务数据；mock 的演示数据、手机外框和预览开关不进入客户端。
- 改变导航结构、主色体系或页面主操作前，先更新规范并获得用户确认；普通排版和状态补全按现有规范实施。
- Android 工程位于 `apps/android/`。UI 改动完成后执行 debug/release 构建、lint 和 `testDebugUnitTest`；构建通过不等于真机视觉验收通过。

## 桌面 UI

- 修改桌面界面前，先阅读 `docs/desktop/UI_DESIGN.md`，这是用户确认并固化的 UI 规范。
- 视觉基准为 `docs/assets/desktop-mantine-mock.html` 与同目录 `desktop-mantine-light.png`、`desktop-mantine-dark.png`、`desktop-mantine-apps.png`、`desktop-mantine-unlock.png`。
- 使用 Mantine（确认基准 9.8.0）+ Tabler Icons，沿用工作侧栏、顶部设备选择、分组/表格、分步解锁与任务抽屉；主题、令牌和控件默认值集中维护，业务布局使用 CSS Modules。
- 正式客户端使用真实设备与任务数据，mock 的演示设备、预设终端输出、预览标识与模拟完成按钮不进入客户端。
- 改变导航、主色体系、页面层级或主操作前，先更新规范和预览并获得用户确认；普通排版修复、无障碍修复和真实状态补全按既有规范实施。
- Windows、macOS 共用 `apps/desktop/`，macOS 仅支持 Apple Silicon。UI 在两个平台的正式 Tauri 窗口中分别验证；浏览器 mock 和构建通过不代替桌面视觉验收。

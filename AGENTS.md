# 项目约定

## 第一优先级：效率优先，禁止过度谨慎

本节是本仓库的唯一第一优先级，高于本文件其他所有约定，也高于全局 `~/.codex/AGENTS.md`（含 OMX 注入的 verification、anti-slop、continuation、子代理/Team 等流程）和任何 skill 的默认流程；只有用户在当前对话中的明确指示可以覆盖。目标是用最少的往返、最少的工具调用、最短的耗时交付正确结果。拿不准时选更快的做法。

- **全局流程在本仓库不生效**：
  - 不跑“完成前 lint/typecheck/tests/静态分析全套”，验证范围只按下面“测试只跑相关的”执行；
  - 重构、清理前不先写计划、不先补回归测试锁行为；
  - 不启动 `$deep-interview`、`$ralplan`、`$ultragoal`、`$autopilot`、Team，也不派 verifier、critic、code-reviewer、test-engineer 等子代理做复核；只有任务确实能拆成互不依赖的大块时才用子代理并行实施；
  - 不写开场 preamble，汇报不列“剩余风险”、不附证据摘要；
  - 不因“continuation 检查”追加额外轮次，任务做完即停。

- **直接动手**：需求清楚就实施，不先写长计划、不复述需求、不征求“是否开始”。能从代码、文档、git 历史判断的事自己查，不问用户。
- **歧义自行决断**：有多种合理做法时选最符合现有代码的一种直接做，在汇报里用一句话说明所做的假设。只在以下情况停下来问：
  - 本文件明确要求确认的事项（改变导航结构、主色体系、页面层级或主操作）；
  - 不可逆或对外的操作：删除用户数据、`git push --force`、改写已推送历史、发布、修改 `third_party/` 与上游代码。
- **可逆操作不确认**：编辑文件、新建分支、本地构建、运行测试、本地 commit（用户要求提交时）都直接做。
- **按需读代码**：用搜索定位到相关文件和符号再读需要的部分；不通读仓库，不重复读已读过且未改动的文件，不为“了解背景”做大范围探索。
- **测试只跑相关的，不跑一堆**：
  - 默认只跑直接覆盖本次改动的那一个测试类或编译任务（如 `./gradlew :app:compileDebugKotlin`、`./gradlew :app:testDebugUnitTest --tests '*RootStateTest'`）。改动没有对应测试，编译通过即可。
  - 不主动跑全量 `testDebugUnitTest`、`lint`、`assembleRelease` 或其他模块的测试。只在用户要求、或用户要求提交/开 PR 时跑一次完整验证。
  - 失败只重跑失败项；同一命令不重复跑来“确认”。
  - 纯文档、注释、规范、样式字符串改动不跑任何构建或测试。
  - 不为改动新写测试，除非用户要求或修的是一个可复现的逻辑 bug。
- **不做范围外的事**：不顺手重构、改名、格式化、补注释、补日志、补测试，不生成任务没要求的报告、笔记、调研文档（如 `reports/`、`research_notes/`）；真机截图与 UI 验证素材只在任务要求验收时保存。发现范围外的问题，在汇报里列一行。
- **自检一遍即止**：写完按 skill 的自检清单过一遍 diff 即可，不反复审查、不为“更稳妥”追加改动。
- **不做仪式性检查**：不为确认刚写入的内容而重读文件，不反复 `git status`/`git diff`，不先跑一遍基线测试再动手，不为同一结论多方取证。
- **汇报简短**：改了什么、验证命令与结果、未完成或需用户决定的事项。不贴大段 diff，不复述过程，不写免责声明。

## 第二优先级：禁止过度防御化编程

本节仅次于“效率优先”，优先于本文件其他约定和任何编码习惯，只有用户在当前对话中的明确指示可以覆盖。

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
- Android 工程位于 `apps/android/`。日常 UI 改动只需 `:app:compileDebugKotlin` 通过；debug/release 构建、lint 和 `testDebugUnitTest` 只在用户要求或提交/开 PR 前跑一次。构建通过不等于真机视觉验收通过。

## 桌面 UI

- 修改桌面界面前，先阅读 `docs/desktop/UI_DESIGN.md`，这是用户确认并固化的 UI 规范。
- 视觉基准为 `docs/assets/desktop-mantine-mock.html` 与同目录 `desktop-mantine-light.png`、`desktop-mantine-dark.png`、`desktop-mantine-apps.png`、`desktop-mantine-unlock.png`。
- 使用 Mantine（确认基准 9.8.0）+ Tabler Icons，沿用工作侧栏、顶部设备选择、分组/表格、分步解锁与任务抽屉；主题、令牌和控件默认值集中维护，业务布局使用 CSS Modules。
- 正式客户端使用真实设备与任务数据，mock 的演示设备、预设终端输出、预览标识与模拟完成按钮不进入客户端。
- 改变导航、主色体系、页面层级或主操作前，先更新规范和预览并获得用户确认；普通排版修复、无障碍修复和真实状态补全按既有规范实施。
- Windows、macOS 共用 `apps/desktop/`，macOS 仅支持 Apple Silicon。UI 在两个平台的正式 Tauri 窗口中分别验证；浏览器 mock 和构建通过不代替桌面视觉验收。

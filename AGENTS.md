# 项目约定

## Android UI

- 修改 Android 界面前，先阅读 `docs/android/UI_DESIGN.md`。这是用户已确认的 UI 规范。
- 视觉基准为 `docs/assets/ui-reset-mock.html` 及同目录 `ui-reset-light.png`、`ui-reset-dark.png`、`ui-reset-mobile.png`。
- 沿用 miuix 0.9.4、四个一级入口、状态优先的页面层级与分组列表。颜色、字号和形状集中在 `ToolboxTheme.kt` 与 `ToolboxComponents.kt`。
- 原生界面使用真实设备与服务数据；mock 的演示数据、手机外框和预览开关不进入客户端。
- 改变导航结构、主色体系或页面主操作前，先更新规范并获得用户确认；普通排版和状态补全按现有规范实施。
- Android 工程位于 `apps/android/`。UI 改动完成后执行 debug/release 构建、lint 和 `testDebugUnitTest`；构建通过不等于真机视觉验收通过。

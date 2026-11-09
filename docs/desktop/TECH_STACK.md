# 桌面端技术栈决定

**状态：用户已确认 Tauri 2 + React + TypeScript + Rust，macOS 仅适配 Apple Silicon；Mantine UI 基准于 2026-11-09（JST）确认并固化。**

## 1. 产品与版本边界

- **v0.1.0 只有 Android 端，唯一产品功能是获取临时 Root。** 权限获取、适配检测、日志和结果验证为该流程提供支撑。
- 桌面端属于后续阶段，版本号与首版功能清单另行确定。
- 桌面端原有核心目标是整合开源方案，提供支持设备的一键强制解锁 Bootloader 流程；现在加入日常 Android 玩机工具箱方向。
- [gjgjxweb](https://github.com/CzJam/gjgjxweb) 是应用管理、安装与文件传输、显示调节、截图、电池记录、终端等功能的参考上游；具体采用内容在实施时确定。

## 2. 技术选择

| 层面 | 决定 |
| --- | --- |
| 桌面框架 | Tauri 2 |
| 界面 | React + TypeScript |
| 组件与图标 | Mantine（确认基准 9.8.0）+ Tabler Icons，界面按 [UI 规范](UI_DESIGN.md) 实施 |
| 样式 | Mantine 主题与组件参数优先；业务布局使用 CSS Modules |
| 前端构建 | Vite |
| 原生层 | Rust；异步任务采用 Tokio |
| Windows 与 macOS 共享方式 | 一套桌面工程，共用界面与主要业务实现，分别构建发布 |
| ADB / Fastboot | 通过原生层调用固定版本、经过验证的原生工具 |
| 解锁方案接入 | 按实际采用上游接入 Python 或原生工具进程，不为统一语言重写底层实现 |
| 终端显示 | xterm.js；会话输入输出由原生层提供 |
| 本地数据 | 设置使用本地文件，任务日志独立保存；当前不引入数据库 |

Tauri 选用 2.x，Mantine 以已确认 mock 的 9.8.0 为基准；其余依赖的精确版本在工程初始化时固定。

工程组织、功能分期、设备与任务流程、构建及验收见 [详细开发文档](DEVELOPMENT.md)。

### 选择依据

- React / TypeScript 便于实现应用列表、文件操作、日志、表单和终端，也便于利用 gjgjxweb 的 JavaScript 实现积累。
- Rust 层统一处理设备状态、长时间任务、外部进程、文件和日志，并向界面提供结构化结果。
- Tauri 支持将不同语言的可执行工具作为 sidecar 随应用分发，适合整合已有解锁实现。
- Tauri 使用系统 WebView，框架本身无需附带 Chromium；最终安装包大小仍取决于 Python 运行环境、原生依赖和设备资源。

## 3. 平台与架构

| 平台 | 目标 | 状态 |
| --- | --- | --- |
| Windows | x64，Rust target 为 `x86_64-pc-windows-msvc` | 沿用选型建议，初始化前确认最低系统版本 |
| macOS | Apple Silicon 原生 arm64，Rust target 为 `aarch64-apple-darwin` | 用户明确确认 |

macOS 不提供 Intel 构建或 universal 包；随包工具与原生库也按 Apple Silicon 准备，不以 Rosetta 为运行前提。最低 macOS 版本在结合 Tauri 与实际采用工具的要求后确定。

Windows、macOS 分别使用对应平台构建与验证。界面和任务代码的跨平台支持，不等于所有候选解锁方案已在两端验证；实际功能按方案、设备、固件和主机平台记录支持范围。

## 4. 实现边界

```text
React + TypeScript
界面、应用列表、表单、进度与终端显示
              │
          Tauri IPC
              │
Rust + Tokio
设备状态、任务流程、外部进程、文件操作、日志
              │
     ┌────────┼───────────┐
    adb     fastboot    解锁工具
                      Python / 原生程序
```

- 界面调用明确的业务操作；设备命令和任务生命周期由原生层负责。
- 设备状态、执行进度、输出和最终结果分别表达；日志中的成功文字不能代替结果检查。
- 文件传输与镜像处理在原生层或工具进程中完成，避免把完整大文件经过界面 IPC 搬运。
- 终端会话的输入、输出、窗口尺寸和结束状态通过专用会话通道传递。
- 外部工具失败与解析错误保留真实原因；实现遵循仓库禁止过度防御化编程约定。
- Python 工具采用时随包提供所需运行环境和依赖，避免要求用户自行安装 Python、pip 或 Homebrew；具体打包方式随上游验证后确定。

### gjgjxweb 的复用边界

参考和复用其设备命令、输出解析、功能交互及终端实现时，将设备通信与界面职责分开。桌面端通过原生 ADB 通道工作，不以 WebUSB 为基础：Tauri 在 Windows 使用 WebView2，在 macOS 使用 WKWebView。

## 5. 工程布局

- 共享桌面工程位于 `apps/desktop/`，前端和 `src-tauri/` 均在该目录下维护。
- 桌面端技术、设计和验证文档位于 `docs/desktop/`。
- 现有 `apps/windows/`、`apps/macos/` 暂保留为平台说明入口，源码由共享桌面工程维护。
- Android 继续由 `apps/android/` 独立构建。跨端代码共享在出现实际需求后确定，不因桌面选型调整 Android 工程。

## 6. 工程初始化时确定的事项

1. Windows、macOS 最低系统版本及依赖精确版本。
2. 首个纳入集成的解锁方案与两端可用的依赖组合。
3. 按已确认的 Mantine [UI 规范](UI_DESIGN.md) 初始化主题、共享组件与正式工程依赖。
4. 原生工具和 Python 环境的打包、签名与发布配置。

## 参考

- [Tauri 架构](https://v2.tauri.app/concept/architecture/)
- [Tauri 外部工具打包（sidecar）](https://v2.tauri.app/develop/sidecar/)
- [Tauri WebView 平台差异](https://v2.tauri.app/reference/webview-versions/)
- [gjgjxweb](https://github.com/CzJam/gjgjxweb)
- [候选上游与用途](../../third_party/README.md)

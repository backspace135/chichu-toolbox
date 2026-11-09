# 桌面客户端

Windows、macOS 共用的桌面工程入口。技术栈已确定为 **Tauri 2 + React + TypeScript + Rust**，前端构建使用 Vite。UI 采用 **Mantine + Tabler Icons**，沿用用户确认的交互预览与 [桌面端 UI 规范](../../docs/desktop/UI_DESIGN.md)。

- macOS 仅支持 Apple Silicon 原生 arm64，不提供 Intel 构建。
- Windows 目标暂按 x64 规划。
- 产品方向为整合开源强制解锁 Bootloader 方案与日常 Android 玩机工具。
- 当前处于技术选型确认阶段，工程尚未初始化，构建命令与最低系统版本待确定。

参见 [桌面端技术栈决定](../../docs/desktop/TECH_STACK.md)、[UI 交互预览](../../docs/assets/desktop-mantine-mock.html)、[仓库布局](../../docs/REPOSITORY_LAYOUT.md)与 [项目总览](../../README.md)。

后续实施按 [详细开发文档](../../docs/desktop/DEVELOPMENT.md) 推进，含功能分期、工程布局、设备与任务模型、解锁接入、双平台打包和 D0–D7 验收条件。文中的工程目录与命令为初始化计划，当前尚未提供可运行客户端。

整体进度见 [桌面端总 issue #10](https://github.com/backspace135/chichu-toolbox/issues/10)。

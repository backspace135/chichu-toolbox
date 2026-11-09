# 桌面端文档

Windows、macOS 共用 Tauri 桌面工程，macOS 仅支持 Apple Silicon。技术栈和 Mantine UI 已确认，正式工程尚待初始化。

总任务：[桌面端总 issue #10：强制解锁 Bootloader + Android 玩机工具箱](https://github.com/backspace135/chichu-toolbox/issues/10)。

## 阅读顺序

1. [技术栈决定](TECH_STACK.md)：已确认选择、平台与原生工具接入边界。
2. [UI 规范](UI_DESIGN.md)：已固化的视觉、布局、交互与变更规则。
3. [详细开发文档](DEVELOPMENT.md)：功能规划、工程结构、设备通信、任务、解锁接入、构建、验证与 D0–D7 里程碑。
4. [Mantine 预览与验证记录](MANTINE_MOCK.md)：[离线 HTML](../assets/desktop-mantine-mock.html) 和截图入口。

## 当前待决策

- 最低 Windows/macOS 版本，以及 Windows x64 正式支持目标。
- 桌面首版功能清单、版本号。
- 可测试的设备与精确固件、首个实际采用的强解方案。
- 初始化依赖版本、工具打包与签名发布配置。

Android v0.1.0 仅获取临时 Root；桌面计划属于后续阶段。UI mock 中出现的设备和功能不代表已经实现或受支持。

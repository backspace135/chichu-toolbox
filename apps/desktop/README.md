# 桌面客户端

Windows、macOS 共用的桌面工程入口。技术栈已确定为 **Tauri 2 + React + TypeScript + Rust**，前端构建使用 Vite。UI 采用 **Mantine + Tabler Icons**，沿用用户确认的交互预览与 [桌面端 UI 规范](../../docs/desktop/UI_DESIGN.md)。

- macOS 仅支持 Apple Silicon 原生 arm64，不提供 Intel 构建。
- Windows 目标暂按 x64 规划。
- 产品目标为自动识别/推荐已验证Bootloader强解方案，并保留gjgjxweb固定基线全部功能。
- 正式工程尚未初始化；本目录为入口占位，构建命令、最低系统与桌面独立版本号待确定，mock不计实现。

参见 [桌面端技术栈决定](../../docs/desktop/TECH_STACK.md)、[UI 交互预览](../../docs/assets/desktop-mantine-mock.html)、[仓库布局](../../docs/REPOSITORY_LAYOUT.md)与 [项目总览](../../README.md)。

后续按 [详细开发文档](../../docs/desktop/DEVELOPMENT.md) 的M0–M6推进（替代旧D0–D7）。M0a=#14–#16，M0b=#17，M1=#18/#19，M2=#20，M3=#21，M4=#22，M5=#23–#30，M6=#31。M4依赖M0b+M2+M3，普通工程不等待M0b。文中目录与命令是初始化契约，当前尚未提供可运行客户端。

[全功能与复用清单](../../docs/desktop/FEATURE_INVENTORY.md) 固定 `CzJam/gjgjxweb@f84f9d50cf5ba63928192e64776081fd1cd9ea60`，逐项记录源码/输入/权限ROM、六入口、原生接口、许可和M5归属。全部功能保持跟踪，首版/后续批次待确认，部分发布不能关闭M5或总目标；Windows/macOS状态均未实现、未验收。上游1.0.0不是本客户端版本。

复用业务/解析/BAT逻辑须落实GPL-3.0-only对应源码及第三方MIT声明；最终发布许可待确认。原生层负责设备/进程/文件/长任务，终端与脚本参与同设备占用；浏览器目录授权和密钥清理按桌面等价实现，只处理本应用拥有的数据。无线配对/收藏不能据未接线函数或依赖能力宣称已有支持。

每日跟踪（#16）区分观测/候选/采用，计划09:00 JST，不在客户端在线替换工具；每版冻结验收SHA。两端正式Tauri窗口、真实设备与无开发环境安装分别验收，截图和编译不能代替强解证据。

整体进度见 [桌面端总 issue #10](https://github.com/backspace135/chichu-toolbox/issues/10)。

M0b 的调查运行时构建/校验入口、M3/M4 条件契约和实际测试结果见 [MTK_RUNTIME.md](../../docs/desktop/MTK_RUNTIME.md)。macOS arm64 隔离启动已验证；Windows 与双平台锁定→解锁真机验收仍阻塞，尚无正式采用方案。

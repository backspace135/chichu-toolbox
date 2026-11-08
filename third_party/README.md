# 第三方源码

上游仓库统一克隆到 `third_party/upstream/`，不直接放在项目根目录。

当前仅用于源码调研与整合评估，克隆不代表已经完成集成、适配或安全审查。
不因仓库包含某个机型名称而认定其支持任意固件。

## 候选仓库

| 本地目录（相对于 `upstream/`） | 上游仓库 | 用途 |
| --- | --- | --- |
| `shizuku` | https://github.com/RikkaApps/Shizuku | 权限服务与启动流程 |
| `shizuku-api` | https://github.com/RikkaApps/Shizuku-API | 权限桥接 API |
| `ghostlock-app` | https://github.com/YuKongA/ghostlock-app | 临时 Root 应用与适配配置 |
| `dfroot` | https://github.com/diabl0w/DFRoot | 临时 Root 候选实现 |
| `dirtyfrag` | https://github.com/mitschud/DirtyFrag | DFRoot 衍生实现，用于对比评估 |
| `kernelsu` | https://github.com/tiann/KernelSU | Root 管理与内核模块 |
| `root-my-galaxy` | https://github.com/BuSung-dev/Root-My-Galaxy | 三星临时 Root 前端候选 |
| `root-my-galaxy-payloads` | https://github.com/BuSung-dev/Root-My-Galaxy-Payloads | 三星适配资源候选 |
| `ghostlock-oneplus` | https://github.com/JoinChang/ghostlock-oneplus | 一加等设备的临时 Root 实现参考 |
| `mi-nobl-root` | https://github.com/xunchahaha/mi_nobl_root | 小米免解 BL Root 方案参考 |
| `mtkclient` | https://github.com/bkerler/mtkclient | 联发科设备维护与通信候选 |
| `xiaomi-hyperos-bootloader-unlock` | https://github.com/Jz8Root/xiaomi-hyperos-bootloader-unlock | 小米联发科 BL 解锁研究 |
| `xiaomi-ubl` | https://github.com/18xjul08/xiaomi_ubl | 小米高通 BL 解锁资料候选 |
| `mi-unlock-tool` | https://github.com/MiForge/MiUnlockTool | 跨平台账号授权解锁客户端候选，并非漏洞强解 |

## 管理约定

- 各目录保留独立 `.git` 和上游 `origin`；当前不是主仓库的 Git 子模块。
- `upstream/` 被主仓库忽略，主仓库克隆不会自动带上这些目录。
- 首次克隆使用上游默认分支，下载完整 Git 历史，不主动递归初始化子模块。
- 不自动执行上游安装脚本、构建脚本、漏洞程序或设备操作。
- 正式集成前确定采用的项目、固定提交、构建方法与设备兼容范围。
- 许可与再分发审查延后处理；保留原有许可证、署名及声明。

## 本次下载记录

14 个仓库均已克隆，下载提交及核验说明见 [SOURCE_SNAPSHOT.md](SOURCE_SNAPSHOT.md)。

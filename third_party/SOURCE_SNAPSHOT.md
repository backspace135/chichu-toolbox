# 上游源码下载快照

记录日期：2026-11-08。

本次下载 14 个独立仓库，均为非浅克隆，保留上游 Git 历史；未初始化子模块。
各仓库的 `origin` 均已核对，与 [README.md](README.md) 中的地址一致。
以下为下载时的默认分支与本地 HEAD，尚未进行构建、适配或安全审查。

| 本地目录（相对于 `upstream/`） | 分支 | 提交 |
| --- | --- | --- |
| `shizuku` | `master` | `b844bc491f1790c72328e1a8e5b2349f8978f0ea` |
| `shizuku-api` | `master` | `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5` |
| `ghostlock-app` | `main` | `4ebc726b9001d79f3c374c917d51569e310df6bc` |
| `dfroot` | `master` | `7a6513a69ce4c1a90b68d857c26347bf166ffe10` |
| `dirtyfrag` | `master` | `2c9cdbace9abbc1b3036a5178bf131b03623131e` |
| `kernelsu` | `main` | `730f644615969cfd4a84b45d7f62a667b7b95a50` |
| `root-my-galaxy` | `main` | `23adf39fcd4f1be4e73184ee960a6d5399baf147` |
| `root-my-galaxy-payloads` | `main` | `6e3223e689688540060ddd97a0927e927bfed207` |
| `ghostlock-oneplus` | `main` | `9b2a7ba61cc2afe0f5652af2f317b62eec68356a` |
| `mi-nobl-root` | `main` | `8ced9300400ddaec6076523b51c86890a51fa84d` |
| `mtkclient` | `main` | `118a000a83ea31c4f427fe5225abfe964546e05e` |
| `xiaomi-hyperos-bootloader-unlock` | `main` | `679709d1cd6ab9f5b41465dcedd9d42ee605586f` |
| `xiaomi-ubl` | `main` | `7bde56dcb0a0aefd7807c76d28c0044e78aa6b60` |
| `mi-unlock-tool` | `main` | `6712a752ecfa28ee66a089637853c1dab0e537ba` |

## 核验情况与限制

- 14 个目录均被主仓库的 `/third_party/upstream/` 忽略规则覆盖。
- 13 个上游工作区干净。
- `dirtyfrag` 初次克隆后，`git status` 即显示 `gradlew.bat` 修改。核查发现索引与工作区都是 CRLF，上游属性为 `text eol=crlf`，本机 `core.autocrlf=true`；`git diff --ignore-space-at-eol --exit-code -- gradlew.bat` 返回 0。差异仅涉及行尾格式，未改写文件、修改配置或重置仓库。
- `shizuku/api` 为未初始化的子模块，固定提交为 `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5`；同一提交的 API 源码已单独下载到 `shizuku-api/`，但这不等于子模块已就绪。
- 未安装依赖、执行上游脚本、构建应用、运行漏洞或操作手机。
- 本记录只标识本次下载状态，不证明兼容性或功能可用；上游目录不是主仓库子模块，克隆主仓库不会自动下载它们。

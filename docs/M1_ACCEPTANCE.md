# M1 验收矩阵

## 运行方式

本地回归：`./gradlew :app:assembleDebug :app:assembleRelease lint testDebugUnitTest`。

真机必须先获得用户同意，再安装开发 APK、执行界面与服务测试。不可自动停止原版 Shizuku、修改第三方应用授权、开启 Root 或更改系统调试设置。

## 当前证据

| 范围 | 状态 | 说明 |
| --- | --- | --- |
| JVM：状态／授权／命令／token／连接／Shell／激活操作隔离／UI 搜索／审查回归 | 已通过 | `chichu-review-final.log`；45 项，失败／错误／跳过均 0；模型不替代设备验收 |
| UI 首轮 debug 打包与 lint | 已通过 | `chichu-ui-build.log`；不包含随后新增图标和许可证资源 |
| 图标、许可证与 release 压缩构建 | 已通过构建及 APK 检查 | `chichu-review-final.log`；debug／release DEX 入口与四 ABI 检查通过；完整 release 尚未安装 |
| 压缩 release 服务 DEX | 部分通过 | release DEX＋已安装 debug native；官方 SDK attach、权限隔离、Shell／UserService UID 2000、销毁及撤销通过；不覆盖压缩管理 UI／完整安装 |
| Android 12 / API 31 | 部分通过 | M2007J22C，ADB 激活、第三方授权、UserService、撤销、服务死亡与重新激活已有设备证据 |
| Android 17 / API 37 | 未验证 | 声明 ACCESS_LOCAL_NETWORK，自动发现时按需申请，拒绝保留手动端口；无对应设备 |
| 无线配对与连接 | 未验证 | 配对码仅保留操作内存；区分配对与连接端口 |
| 电脑 ADB 激活 | 已通过 | 动态安装路径启动 `libchichu_starter.so`；服务进程 UID 2000，管理界面已连接 |
| 第三方官方 SDK／UserService | 已通过本机基本流程 | 官方 Maven 13.1.5；未授权拒绝、真实批准／拒绝、Shell id、UserService UID 2000 和销毁、撤销后拒绝 |
| 非管理 UID／伪造 token | 部分通过 | 未授权和已授权客户端均不能调用 10001–10004；真实弹窗不被伪造 token 关闭，拒绝回调正常；未穷举设备攻击面 |
| daemon 无活客户端撤销 | 已通过设备基本流程 | force-stop 验收客户端后 Shell probe 存活，工具箱撤销后消失，新客户端执行被拒；排队竞态未穷举 |
| UID 回收／双真实请求交错／NSD 代际 | 模型通过 | 尚无对应设备复现／自动发现端到端证据 |
| 无 upstream 源码副本构建 | 较早版本已通过 | `chichu-clean-9490cf9e`，重新构建四 ABI；`chichu-clean-build.log`；最终审查修复后尚未重测独立副本 |
| 原版共存与连接顺序 | 未验证 | 未发现正在运行的同名服务；不得主动干预原版 |
| 图标 MCP 预览 | 受阻 | 无图像生成／Better Design MCP；Playwright 导航先超时、再报告浏览器已关闭，未取得有效预览 |

## 真机检查清单

- 未激活、激活中、成功、死亡、失败状态与重试；前台恢复重新检查。
- 手机底栏与宽屏侧栏互斥，内容宽度 ≤800dp；深浅色、动态配色和 200% 字体。
- 系统栏、手势区、键盘、配对弹层与长命令无内容遮挡。
- 无线端口校验、配对码错误、连接失败、取消后旧任务不改变新任务状态。
- 第三方官方 Provider attach、未授权调用失败、批准／拒绝／撤销、重启持久化。
- 伪造／过期／重放 token 不授权；非管理器不能调用 10001–10004 或管理 AIDL。
- UserService 启动、销毁与撤销清理；Shell UID 明确为 2000。
- 原版先启动／内置先启动／一方死亡后的第三方连接归属；不杀原版。
- APK 中服务入口、starter／rish／adb native、图标和完整许可证存在；release 保留反射与 JNI 入口。

未填写设备证据的项目均不是「已完成」。

详细结果与已知限制见 [M1_VALIDATION_RESULTS.md](M1_VALIDATION_RESULTS.md)。

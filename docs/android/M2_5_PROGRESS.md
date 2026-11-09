# M2.5 阶段：Android UI 与交互完善

日期：2026-11-09（JST）。基线：`d1de0ba`。按用户确认的 UI mock 落实原生界面，并补齐真机反馈中的切页和无线配对体验。

## 交付

- 固化 [UI 规范](UI_DESIGN.md)、交互 HTML 和深浅色预览；沿用 miuix 0.9.4、四个一级入口、真实服务与设备数据。
- 重组权限服务、授权列表、设备诊断和设置；支持搜索筛选、分组列表、详情弹层、深浅色和动态色。
- 修复 200% 字体导航重叠、主题选项不可达、深色系统栏和弹层按钮被导航栏/键盘遮挡。
- 通知栏无线配对：前台本机服务发现、六位码内联输入、停止搜索、超时和结果通知；与页面共用激活门禁。配对码不持久化、不记录、不重投递恢复。
- 补齐 AndroidHiddenApiBypass 6.1，只按需豁免配对所需的 Conscrypt TLS exporter，修复 Android 12 的 `NoSuchMethodError`；保留不兼容失败提示。
- 切页改为手势驱动的 `HorizontalPager`；连续点击保留位置与速度、重复目标去重、旧任务隔离；保留四页组合状态并隔离导航重组，避免重复系统栏设置。

## 已执行验证

```sh
# 工作目录：apps/android/
bash ./gradlew :app:assembleDebug :app:assembleRelease lint testDebugUnitTest --no-daemon
python3 scripts/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

- debug、unsigned release、lint 与 JVM 单测成功；61 项测试，失败/错误/跳过均为 0。
- app lint 0 错误、16 警告；shizuku lint 0 错误、19 警告，其余模块 0 错误。未宣称零警告。
- 两个 APK 入口、四 ABI native 和许可证核验通过；最终 debug 已安装到设备。
- 设备：M2007J22C / cannon，Android 12 API 31，MIUI 14，392dp 手机及横屏侧栏。
- 已观察浅色、深色、动态色、200% 字体、居中主题对话框和键盘下操作入口。
- 通知自动发现、六位码配对、成功结果、无效输入重试和停止搜索通过。MIUI 经典通知样式可能不显示内联输入，原生样式验证通过，应用内有对应说明。
- 本机无线连接与内置服务激活成功，真实身份 Shell UID 2000。
- 仅对自建验收客户端测试允许、拒绝、撤销；批准后 Shell 与 UserService UID 2000，撤销后未授权调用被拒，管理事务仍被拦截。
- 拖动中间位置保持、反向拖动、松手归位、连续点击最终目标、返回首页和筛选状态恢复已有设备证据。

### 连续切换采样

同一设备、debug 包、同一组 32 次跨页点击，切换前重置 `dumpsys gfxinfo`。优化后的两轮采样在最后一次手势/点击优先级对齐前取得：

| 样本 | 卡顿帧 / 总帧 | P95 | P99 |
| --- | --- | --- | --- |
| 修复前 1 | 5 / 105（4.76%） | 24ms | 89ms |
| 修复前 2 | 12 / 101（11.88%） | 81ms | 85ms |
| 优化后 1 | 2 / 100（2.00%） | 34ms | 42ms |
| 优化后 2 | 1 / 96（1.04%） | 20ms | 22ms |

数据说明改善趋势，不代表所有场景零掉帧。最终采样遇到横屏、点击未命中而得到 0 帧，已排除，不作为性能通过依据。最后的优先级对齐已通过完整构建；按用户要求停止扩展测试并交付。

## 范围与本地证据

- 截图、XML、录屏与原始帧统计仅保留在本地 `docs/assets/ui-validation-20261109/`，不纳入 PR。
- Android 17 权限实际交互、完整 release 安装运行、TalkBack 全矩阵、平板全矩阵和共存顺序矩阵未覆盖；M2.5 不代表 Root / KernelSU 能力实现或 M1 全量验收。
- 字体已恢复为 1.0，自动旋转已恢复开启，MIUI 通知样式已切回经典；最后一次恢复无线调试关闭的命令遇到 USB 设备断开，未确认完成。

# 官方 SDK 真机验收客户端

独立工程，不加入主工程五模块。使用 Maven 官方 `api/provider:13.1.5`，不修改客户端 SDK。仅供获得同意后的开发设备验收，不作为正式功能交付。

```powershell
.\gradlew.bat -p testing/sdk-client assembleDebug --no-daemon
adb -s <serial> install -r testing/sdk-client/build/outputs/apk/debug/ChichuSdkAcceptance-debug.apk
adb -s <serial> shell am start -W -n com.chichu.toolbox.acceptance/.ProbeActivity --es mode probe
adb -s <serial> logcat -d -s ChichuAcceptance:I
```

`mode=request` 发起真实权限请求；在工具箱中明确允许或拒绝，再以 `mode=probe` 验证。已拒绝时先在工具箱里对**此测试包**选择「撤销并恢复询问」，不要修改其他应用授权。

日志预期：

- 未授权：`PERMISSION=-1`、`UNAUTHORIZED_PROCESS_BLOCKED=true`。
- 任意授权状态：`MANAGER_BLOCKED=10001` 至 `10004`。
- 批准：`REQUEST_RESULT=0`，随后 `SHELL=uid=2000... EXIT=0`、`USER_SERVICE_UID=2000`。
- 绑定后 10 秒没有回调：`FAIL_USER_SERVICE_TIMEOUT`。任何 `FAIL_` 或异常均不算通过。

Shell 协议测试通过官方传递依赖中的 `IShizukuService` 调用 `newProcess`；13.1.5 的 `Shizuku.newProcess` 为 private，不把它描述成公开 API。

`mode=daemon` 保留验收 UserService，供“客户端进程退出后撤销”回归：确认 probe 子进程存在，再只结束验收客户端、在工具箱撤销该测试包，检查 probe 子进程消失。不要将此模式用于现有第三方应用。

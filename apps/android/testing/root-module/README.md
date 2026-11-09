# M3 本地模块验收夹具

仅包含模块元数据、安装提示、模块自身目录内的 action 标记与 WebUI。没有开机脚本、系统挂载、属性修改或重启。仅在用户允许的真机手动安装用于验收；不随客户端打包。

在 `apps/android/` 执行：

```sh
python3 scripts/package-root-fixture.py --version 1
# 升级验证
python3 scripts/package-root-fixture.py --version 2
```

ZIP 输出到 `build/root-fixture/`。通过工具箱模块页选择 ZIP；启停、action、WebUI、更新和卸载仅操作 `chichu_m3_acceptance`。卸载在 ksud 返回结果后可能等待重启，不为测试重启设备。

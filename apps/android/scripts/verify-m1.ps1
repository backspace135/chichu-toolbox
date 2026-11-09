param(
    [string]$Adb = 'adb',
    [Parameter(Mandatory = $true)][string]$Serial,
    [switch]$InstallDebug,
    [switch]$Activate,
    [string]$Apk = "$PSScriptRoot/../app/build/outputs/apk/debug/app-debug.apk"
)

$ErrorActionPreference = 'Stop'

function Invoke-Adb {
    & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB 执行失败，退出码：$LASTEXITCODE" }
}

# 默认只读取设备状态；安装和重启内置服务必须明确选择对应参数。
Invoke-Adb get-state
Invoke-Adb shell getprop ro.product.model
Invoke-Adb shell getprop ro.build.version.sdk
if ($InstallDebug) { Invoke-Adb install -r (Resolve-Path $Apk).Path }
$package = @(Invoke-Adb shell pm path com.chichu.toolbox)
$base = $package | Where-Object { $_.Trim().EndsWith('/base.apk') } | Select-Object -First 1
if (-not $base) { throw '未找到工具箱主 APK；请先安装，或明确指定 -InstallDebug。' }
$base = $base.Trim().Substring('package:'.Length)
Write-Output "工具箱 APK：$base"
if ($Activate) {
    $directory = $base.Substring(0, $base.LastIndexOf('/'))
    $abi = (Invoke-Adb shell getprop ro.product.cpu.abi).Trim()
    $nativeAbi = switch ($abi) {
        'arm64-v8a' { 'arm64' }
        'armeabi-v7a' { 'arm' }
        'x86' { 'x86' }
        'x86_64' { 'x86_64' }
        default { throw "不支持的设备 ABI：$abi" }
    }
    Invoke-Adb shell "'$directory/lib/$nativeAbi/libchichu_starter.so' --apk='$base'"
}
Invoke-Adb shell 'ps -A -o UID,PID,NAME' | Select-String 'chichu|shizuku'
Write-Output '此脚本不自动修改授权、不操作原版 Shizuku、不更改系统调试设置；进程存在不等于 Binder 或无线流程验收通过。'

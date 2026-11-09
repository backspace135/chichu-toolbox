package com.chichu.toolbox

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DiagnosticsScreen(model: ToolboxViewModel) {
    val diagnostics by model.diagnostics.collectAsStateWithLifecycle()
    val config by model.configImport.collectAsStateWithLifecycle()
    val selectConfig = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importKernelConfig)
    }
    Page("设备诊断") {
        Group {
            Text("设备与内核信息", style = MiuixTheme.textStyles.title2)
            Hint("只读检测，无需激活权限服务。回到前台时会重新读取设备信息。")
            when {
                diagnostics.loading -> Text("正在检测…")
                diagnostics.error != null -> Text(diagnostics.error!!)
                diagnostics.snapshot == null -> Text("尚未检测")
                else -> diagnostics.snapshot?.let { device ->
                    Text("${device.manufacturer} ${device.model}", style = MiuixTheme.textStyles.headline2)
                    Hint("${device.device} · Android ${device.androidRelease} · API ${device.sdk}")
                    Hint("安全补丁：${device.securityPatch.ifEmpty { "系统未提供" }}")
                    Hint("支持的 ABI：${device.supportedAbis.joinToString()}")
                    Text("完整内核版本", style = MiuixTheme.textStyles.headline2)
                    SelectionContainer { Text(device.kernelRelease, fontFamily = FontFamily.Monospace) }
                    SelectionContainer { Hint(device.kernelVersion) }
                    if (device.parsedKernel == null) Hint("无法解析数字版本；保留系统提供的完整原文。")
                    Text("系统构建指纹", style = MiuixTheme.textStyles.headline2)
                    SelectionContainer { Hint(device.fingerprint) }
                    Hint("本应用进程 UID：${device.processUid}。应用 UID 不能证明设备有无 Root。")
                }
            }
            Action("重新检测", enabled = !diagnostics.loading, onClick = model::refreshDiagnostics)
        }
        Group("本地内核配置") {
            Text("导入 .config / .config.gz", style = MiuixTheme.textStyles.headline2)
            Hint("检查独立 Linux 内核配置文本的格式，统计配置项并计算 SHA-256。源文件与解压文本均限 2 MiB。")
            Action(if (config.loading) "正在校验…" else "选择配置文件", primary = true, enabled = !config.loading) {
                selectConfig.launch(arrayOf("*/*"))
            }
            config.error?.let { Text("校验失败：$it") }
            config.summary?.let { summary ->
                Text("配置文本格式有效", style = MiuixTheme.textStyles.headline2)
                Hint("${if (summary.compressed) "gzip 压缩" else "纯文本"} · 解码后 ${summary.decodedBytes} 字节 · ${summary.optionCount} 项")
                Hint("内建 ${summary.builtInCount} · 模块 ${summary.moduleCount} · 禁用 ${summary.disabledCount} · 其他值 ${summary.otherCount}")
                Text("解压后原始字节 SHA-256", style = MiuixTheme.textStyles.headline2)
                SelectionContainer { Text(summary.sha256, fontFamily = FontFamily.Monospace) }
            }
            Hint("导入结果仅保留在当前进程内存中；不修改源文件。格式有效不代表配置来自当前设备，也不代表与当前内核兼容。")
        }
        Group("验证范围") {
            Text("运行能力尚未验证", style = MiuixTheme.textStyles.headline2)
            Hint("设备支持情况：未验证\n设备 Root 状态：未探测\nKernelSU 加载状态：未探测\n管理通道：未探测")
            Hint("此页的设备信息与配置格式校验不能替代上述运行状态的独立验证。")
        }
    }
}

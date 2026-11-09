package com.chichu.toolbox

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DiagnosticsScreen(model: ToolboxViewModel) {
    val diagnostics by model.diagnostics.collectAsStateWithLifecycle()
    val config by model.configImport.collectAsStateWithLifecycle()
    var details by rememberSaveable { mutableStateOf(false) }
    val selectConfig = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importKernelConfig)
    }
    Page("设备诊断", MiuixIcons.Refresh, "重新读取设备信息", model::refreshDiagnostics) {
        when {
            diagnostics.loading -> Group { Text("正在检测…"); Hint("只读检测，无需激活权限服务。") }
            diagnostics.error != null -> Group {
                Text("无法读取设备信息", style = MiuixTheme.textStyles.headline2)
                Hint(diagnostics.error!!)
                Action("重新检测", onClick = model::refreshDiagnostics)
            }
            diagnostics.snapshot == null -> Group { Text("尚未检测"); Action("开始检测", onClick = model::refreshDiagnostics) }
            else -> diagnostics.snapshot?.let { device ->
                ListCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(MiuixTheme.colorScheme.primaryContainer, RoundedCornerShape(ToolboxStyle.iconRadius)), contentAlignment = Alignment.Center) {
                            Icon(ToolboxIcons.Device, contentDescription = null, modifier = Modifier.size(25.dp), tint = MiuixTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(device.model, style = MiuixTheme.textStyles.title3)
                        Hint("${device.manufacturer} · ${device.device}")
                        SpecificationGrid(listOf(
                            "Android 版本" to "Android ${device.androidRelease}",
                            "API 级别" to device.sdk.toString(),
                            "安全补丁" to device.securityPatch.ifEmpty { "系统未提供" },
                            "处理器架构" to device.supportedAbis.joinToString().ifEmpty { "系统未提供" },
                        ))
                    }
                }
                SectionLabel("内核与系统")
                ListCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Hint("内核版本")
                        SelectionContainer { Text(device.kernelRelease, fontFamily = FontFamily.Monospace, style = MiuixTheme.textStyles.body2) }
                        if (device.parsedKernel == null) Hint("无法解析数字版本，保留系统提供的完整原文。")
                    }
                    ListDivider(inset = 16.dp)
                    PreferenceRow("完整系统信息", "构建指纹、内核原文与应用 UID", ToolboxIcons.Diagnostics) { details = true }
                }
            }
        }
        SectionLabel("本地内核配置")
        ListCard {
            PreferenceRow(
                if (config.loading) "正在校验…" else "导入配置文件", "支持 .config / .config.gz", MiuixIcons.File,
                enabled = !config.loading,
            ) { selectConfig.launch(arrayOf("*/*")) }
        }
        Footnote("只校验文本格式，不修改源文件；原文件与解压文本均限 2 MiB。")
        config.error?.let { error ->
            Group {
                Text("配置校验失败", color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.headline2)
                Hint(error)
                Action("重新选择配置文件", enabled = !config.loading) { selectConfig.launch(arrayOf("*/*")) }
            }
        }
        config.summary?.let { summary ->
            Group {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(ToolboxIcons.CheckCircle, contentDescription = null, tint = ToolboxStyle.connectedContent, modifier = Modifier.size(24.dp))
                    Text("配置文本格式有效", style = MiuixTheme.textStyles.headline2, color = ToolboxStyle.connectedContent)
                }
                Hint(if (summary.compressed) "gzip 压缩文件" else "纯文本文件")
                SpecificationGrid(listOf(
                    "配置项" to "${summary.optionCount} 项",
                    "解压后文本" to "${summary.decodedBytes} 字节",
                    "内建 / 模块" to "${summary.builtInCount} / ${summary.moduleCount}",
                    "禁用 / 其他值" to "${summary.disabledCount} / ${summary.otherCount}",
                ))
                Hint("解压后原始字节 SHA-256")
                SelectionContainer { Text(summary.sha256, fontFamily = FontFamily.Monospace, style = MiuixTheme.textStyles.body2) }
                Hint("结果仅保留在当前进程内存中。格式有效不代表文件来自当前设备，也不代表与当前内核兼容。")
            }
        }
        SectionLabel("运行能力", "尚未验证")
        ListCard {
            CapabilityRow("设备支持情况", "未验证")
            ListDivider(inset = 16.dp)
            CapabilityRow("设备 Root 状态", "未探测")
            ListDivider(inset = 16.dp)
            CapabilityRow("KernelSU 加载状态", "未探测")
            ListDivider(inset = 16.dp)
            CapabilityRow("管理通道", "未探测")
        }
        Footnote("设备信息、应用 UID 与配置格式不能替代运行能力的独立验证。")
    }
    DetailSheet(details, "完整系统信息", { details = false }) {
        when {
            diagnostics.loading -> Hint("正在重新读取设备信息…")
            diagnostics.error != null -> Hint(diagnostics.error!!)
            else -> diagnostics.snapshot?.let { device ->
                Text("${device.manufacturer} ${device.model}", style = MiuixTheme.textStyles.headline2)
                Hint("${device.device} · Android ${device.androidRelease} · API ${device.sdk}")
                Hint("支持的 ABI：${device.supportedAbis.joinToString()}")
                Text("完整内核版本", style = MiuixTheme.textStyles.headline2)
                SelectionContainer { Text("${device.kernelRelease}\n${device.kernelVersion}", fontFamily = FontFamily.Monospace, style = MiuixTheme.textStyles.body2) }
                Text("系统构建指纹", style = MiuixTheme.textStyles.headline2)
                SelectionContainer { Text(device.fingerprint, fontFamily = FontFamily.Monospace, style = MiuixTheme.textStyles.body2) }
                Hint("本应用进程 UID：${device.processUid}。应用 UID 不能证明设备有无 Root。")
            }
        }
    }
}

@Composable
private fun SpecificationGrid(values: List<Pair<String, String>>) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val columns = if (maxWidth < 260.dp || LocalDensity.current.fontScale >= 1.5f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            values.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Hint(label)
                            Text(value, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MiuixTheme.textStyles.body2, modifier = Modifier.weight(1f))
        Hint(value, Modifier.background(MiuixTheme.colorScheme.secondaryVariant, RoundedCornerShape(ToolboxStyle.tagRadius)).padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

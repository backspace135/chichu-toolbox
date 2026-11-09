package com.chichu.toolbox

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chichu.toolbox.core.common.ServiceState
import com.chichu.toolbox.core.shizuku.activation.ActivationStage
import com.chichu.toolbox.core.shizuku.activation.ActivationStatus
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ServiceScreen(model: ToolboxViewModel, manage: () -> Unit, diagnostics: () -> Unit) {
    val service by model.service.collectAsStateWithLifecycle()
    val activation by model.activation.status.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val connected = service is ServiceState.Connected
    Page("权限服务", onAction = { sheet = "help" }) {
        ServiceStatusCard(service, activation)
        Action(
            text = if (connected) "管理应用授权" else "无线调试激活",
            primary = true, enabled = connected || !activation.busy,
            icon = if (connected) MiuixIcons.GridView else ToolboxIcons.Wireless,
        ) { if (connected) manage() else sheet = "wireless" }
        if (activation.busy) Action("取消激活", onClick = model.activation::cancel)
        SectionLabel("激活与连接")
        ListCard {
            PreferenceRow("无线调试", "无需电脑，在本机配对并激活", ToolboxIcons.Wireless, !activation.busy) { sheet = "wireless" }
            ListDivider()
            PreferenceRow("电脑 ADB", "通过 USB 连接电脑激活", ToolboxIcons.Terminal) { sheet = "computer" }
            if (activation.log.isNotEmpty()) {
                ListDivider()
                PreferenceRow("启动日志", "查看本次服务的连接记录", MiuixIcons.File) { sheet = "logs" }
            }
        }
        Footnote("提供 Shell 权限；重启设备后需要重新激活。")
        SectionLabel("当前设备")
        ListCard {
            PreferenceRow("${Build.MANUFACTURER} ${Build.MODEL}", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}", ToolboxIcons.Device, onClick = diagnostics)
        }
        if (model.originalInstalled) {
            Footnote("已安装原版 Shizuku，运行状态未确认。")
            Action("了解共存方式") { sheet = "coexist" }
        }
    }
    ActivationSheets(model, sheet) { sheet = null }
    DetailSheet(sheet == "help", "权限服务说明", { sheet = null }) {
        Hint("工具箱提供独立的内置 Shell 服务。通过无线调试或电脑 ADB 激活后，可为支持 Shizuku API 的应用管理授权。")
        Hint("Shell 与 Root 权限相互独立。服务状态以真实连接与身份校验为准。")
    }
    CoexistSheet(sheet == "coexist", model.originalInstalled) { sheet = null }
}

@Composable
private fun ServiceStatusCard(service: ServiceState, activation: ActivationStatus) {
    val connected = service as? ServiceState.Connected
    val failed = service is ServiceState.Failed || (!activation.busy && connected == null && activation.stage == ActivationStage.Failed)
    val presentation = service.presentation()
    val title = when {
        connected != null -> presentation.title
        activation.busy -> "正在连接"
        failed -> "连接失败"
        else -> presentation.title
    }
    val detail = when {
        connected != null -> "服务运行正常，应用可请求 Shell 权限"
        service is ServiceState.Failed && !activation.busy -> presentation.detail
        activation.message.isNotEmpty() && activation.stage != ActivationStage.Connected -> activation.message
        else -> presentation.detail
    }
    val contentColor = when {
        connected != null -> ToolboxStyle.connectedContent
        failed -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Card(
        modifier = Modifier.fillMaxWidth(), cornerRadius = ToolboxStyle.statusRadius,
        colors = CardDefaults.defaultColors(color = if (connected != null) ToolboxStyle.connectedContainer else MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (connected != null && LocalDensity.current.fontScale < 1.5f) {
                Icon(
                    ToolboxIcons.CheckCircle, contentDescription = null, tint = contentColor.copy(alpha = 0.20f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 22.dp).offset(x = 10.dp).size(110.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(contentColor))
                    Text("内置权限服务", style = MiuixTheme.textStyles.body2, color = contentColor)
                }
                Text(title, style = MiuixTheme.textStyles.title2, modifier = Modifier.padding(top = 2.dp))
                Text(detail, style = MiuixTheme.textStyles.body2, color = contentColor)
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = contentColor.copy(alpha = 0.13f))
                if (connected != null) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("服务 ${connected.identity.version}.${connected.identity.patchVersion}", style = MiuixTheme.textStyles.body2, color = contentColor, modifier = Modifier.padding(vertical = 5.dp))
                        Text(
                            "Shell · UID ${connected.identity.uid}", style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium), color = contentColor,
                            modifier = Modifier.background(contentColor.copy(alpha = 0.07f), RoundedCornerShape(ToolboxStyle.tagRadius)).padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                } else Hint("支持无线调试与电脑 ADB 激活")
            }
        }
    }
}

@Composable
internal fun CoexistSheet(show: Boolean, installed: Boolean, onDismiss: () -> Unit) {
    DetailSheet(show, "与原版 Shizuku 共存", onDismiss) {
        Text(if (installed) "已安装原版 Shizuku" else "未检测到原版 Shizuku", style = MiuixTheme.textStyles.headline2)
        Hint("安装状态不等于服务运行状态。工具箱不会自动停止或接管原版；第三方应用的连接归属需按实际连接情况检查。")
        Hint("内置服务使用独立授权配置，不读取或修改原版授权。")
    }
}

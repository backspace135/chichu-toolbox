package com.chichu.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chichu.toolbox.core.common.ServiceState
import com.chichu.toolbox.core.common.ShellGrant
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ApplicationsScreen(model: ToolboxViewModel, activate: () -> Unit) {
    val state by model.applications.collectAsStateWithLifecycle()
    val service by model.service.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val filters = listOf(null, ShellGrant.Allowed, ShellGrant.Ask, ShellGrant.Denied)
    val filterLabels = listOf("全部", "已允许", "待询问", "已拒绝")
    val connected = service is ServiceState.Connected
    LaunchedEffect(connected) { if (!connected) selectedPackage = null }
    val visible = state.items.matching(query).filter { filters[filter] == null || it.grant == filters[filter] }
    LazyPage("应用授权", MiuixIcons.Refresh, "刷新应用列表", model::refresh) {
        if (!connected) {
            item {
                Group {
                    Text("请先激活权限服务", style = MiuixTheme.textStyles.headline2)
                    Hint("服务离线时无法读取或管理 Shell 授权，这不表示没有应用。")
                    Action("前往激活", primary = true, onClick = activate)
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ApplicationSearch(query) { query = it }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filterLabels.forEachIndexed { index, label ->
                            TextButton(
                                text = label, onClick = { filter = index }, minHeight = 48.dp, minWidth = 48.dp, cornerRadius = ToolboxStyle.filterRadius,
                                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                textStyle = MiuixTheme.textStyles.body2,
                                modifier = Modifier.semantics { selected = filter == index },
                                colors = ButtonDefaults.textButtonColors(
                                    color = if (filter == index) MiuixTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    textColor = if (filter == index) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                ),
                            )
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Shell 权限", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.padding(end = 16.dp))
                        if (!state.loading && state.error == null) Hint("${state.items.size} 个应用 · ${state.items.count { it.grant == ShellGrant.Allowed }} 个已允许")
                    }
                }
            }
            when {
                state.loading -> item { Group { Text("正在加载应用…"); Hint("正在从内置服务读取授权状态。") } }
                state.error != null -> item {
                    Group {
                        Text("无法加载应用", style = MiuixTheme.textStyles.headline2)
                        Hint(state.error!!)
                        Action("重新加载", onClick = model::refresh)
                    }
                }
                state.items.isEmpty() -> item { Group { Text("暂无兼容应用", style = MiuixTheme.textStyles.headline2); Hint("安装支持 Shizuku API 的应用后，点击右上角刷新。") } }
                visible.isEmpty() -> item { Group { Text("没有匹配的应用", style = MiuixTheme.textStyles.headline2); Hint("试试其他应用名、包名或授权筛选。") } }
                else -> itemsIndexed(visible, key = { _, item -> item.packageName }) { index, item ->
                    ApplicationRow(
                        item = item, first = index == 0, last = index == visible.lastIndex,
                        enabled = state.changingUid == null,
                    ) { selectedPackage = item.packageName }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("点击应用管理授权。同一 UID 下的应用共享授权状态；Shell 授权与 Root 权限相互独立。")
                }
            }
        }
    }
    val selected = state.items.firstOrNull { it.packageName == selectedPackage }
    DetailSheet(selected != null && connected, "管理 Shell 授权", { selectedPackage = null }) {
        selected?.let { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(item.packageName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.label, style = MiuixTheme.textStyles.headline2)
                    Hint("${item.packageName}\nUID ${item.uid}")
                }
            }
            Hint("当前状态：${item.grant.label}。此授权仅提供 Shell 权限，同一 UID 下的应用会一并生效。")
            Action("允许 Shell 权限", primary = true, enabled = state.changingUid == null && !state.loading) {
                model.setGrant(item, ShellGrant.Allowed); selectedPackage = null
            }
            Action("拒绝", enabled = state.changingUid == null && !state.loading) {
                model.setGrant(item, ShellGrant.Denied); selectedPackage = null
            }
            Action("撤销并恢复询问", enabled = state.changingUid == null && !state.loading) {
                model.setGrant(item, ShellGrant.Ask); selectedPackage = null
            }
        }
    }
}

@Composable
private fun ApplicationSearch(query: String, onQuery: (String) -> Unit) {
    BasicTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        textStyle = MiuixTheme.textStyles.body2.copy(color = MiuixTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(MiuixTheme.colorScheme.secondaryVariant, RoundedCornerShape(ToolboxStyle.searchRadius))
            .semantics { contentDescription = "搜索应用名称或包名" },
        decorationBox = { innerTextField ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MiuixIcons.Search, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Hint("搜索应用名称或包名")
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun ApplicationRow(item: ApplicationItem, first: Boolean, last: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(
        topStart = if (first) ToolboxStyle.cardRadius else 0.dp, topEnd = if (first) ToolboxStyle.cardRadius else 0.dp,
        bottomStart = if (last) ToolboxStyle.cardRadius else 0.dp, bottomEnd = if (last) ToolboxStyle.cardRadius else 0.dp,
    )
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val statusColor = when (item.grant) {
        ShellGrant.Allowed -> ToolboxStyle.connectedContent
        ShellGrant.Denied -> MiuixTheme.colorScheme.error
        ShellGrant.Ask -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Column(Modifier.fillMaxWidth().clip(shape).background(MiuixTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(item.packageName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.label, style = MiuixTheme.textStyles.headline2)
                Hint(item.packageName)
                if (largeText) Text(item.grant.label, style = MiuixTheme.textStyles.body2, color = statusColor)
            }
            if (!largeText) Text(item.grant.label, style = MiuixTheme.textStyles.body2, color = statusColor)
        }
        if (!last) ListDivider(inset = 72.dp)
    }
}

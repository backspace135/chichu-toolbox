package com.chichu.toolbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.horizontalPagerSwipeOverride

private val destinations = listOf("service" to "权限服务", "applications" to "应用授权", "diagnostics" to "设备诊断", "settings" to "设置")

@Composable
fun ToolboxApp(model: ToolboxViewModel) {
    val appearance by model.appearance.collectAsStateWithLifecycle()
    ToolboxTheme(appearance) {
        val pager = rememberPagerState(pageCount = { destinations.size })
        val scope = rememberCoroutineScope()
        val navigation = remember(pager, scope) { ToolboxPagerState(pager, scope) }
        PagerBackHandler(navigation)
        BoxWithConstraints(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).safeDrawingPadding().imePadding()) {
            val wide = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) NavigationRail(expanded = false, defaultWindowInsetsPadding = false) {
                    ToolboxRailItems(navigation)
                }
                Column(Modifier.weight(1f)) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.weight(1f).horizontalPagerSwipeOverride(pager, onIntercepted = navigation::onDrag),
                        // Only four lightweight top-level screens: retain them rather than
                        // recomposing pages and reloading icons in the middle of rapid reversals.
                        beyondViewportPageCount = destinations.lastIndex,
                        userScrollEnabled = false,
                        overscrollEffect = null,
                        pageNestedScrollConnection = PagerGestureNestedScrollConnection,
                        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = PagerNavigationSpringSpec),
                        key = { destinations[it].first },
                    ) { page ->
                        when (page) {
                            0 -> ServiceScreen(model, { navigation.select(1) }, { navigation.select(2) })
                            1 -> ApplicationsScreen(model) { navigation.select(0) }
                            2 -> DiagnosticsScreen(model)
                            3 -> SettingsScreen(model)
                        }
                    }
                    if (!wide) ToolboxBottomNavigation(navigation)
                }
            }
        }
        PermissionDialog(model)
        val message by model.message.collectAsStateWithLifecycle()
        WindowDialog(show = message != null, title = "操作提示", summary = message, onDismissRequest = model::clearMessage) {
            Action("知道了", onClick = model::clearMessage)
        }
    }
}

@Composable
private fun PagerBackHandler(navigation: ToolboxPagerState) {
    val enabled by remember(navigation) { derivedStateOf { navigation.selectedPage != 0 } }
    BackHandler(enabled) { navigation.select(0) }
}

@Composable
private fun ToolboxBottomNavigation(navigation: ToolboxPagerState) {
    val selectedPage = navigation.selectedPage
    val icons = listOf(MiuixIcons.Home, MiuixIcons.GridView, ToolboxIcons.Diagnostics, MiuixIcons.Settings)
    NavigationBar(defaultWindowInsetsPadding = false) {
        destinations.forEachIndexed { index, destination ->
            ToolboxNavigationItem(selectedPage == index, { navigation.select(index) }, icons[index], destination.second)
        }
    }
}

@Composable
private fun ToolboxRailItems(navigation: ToolboxPagerState) {
    val selectedPage = navigation.selectedPage
    val icons = listOf(MiuixIcons.Home, MiuixIcons.GridView, ToolboxIcons.Diagnostics, MiuixIcons.Settings)
    destinations.forEachIndexed { index, destination ->
        NavigationRailItem(selectedPage == index, { navigation.select(index) }, icons[index], destination.second)
    }
}

@Composable
internal fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
            ?: Icon(MiuixIcons.GridView, contentDescription = null, modifier = Modifier.size(28.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun PermissionDialog(model: ToolboxViewModel) {
    val prompt by model.prompt.collectAsStateWithLifecycle()
    val busy by model.promptBusy.collectAsStateWithLifecycle()
    val request = prompt
    BackHandler(request != null) { model.answerPermission(false) }
    WindowDialog(show = request != null, title = "允许 Shell 权限？", onDismissRequest = { model.answerPermission(false) }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            request?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(it.packageName)
                    Text(it.packageName, style = MiuixTheme.textStyles.headline2, modifier = Modifier.weight(1f))
                }
                Hint("UID ${it.uid} · 身份已由内置服务验证\n允许后，此应用可通过 Shell 操作设备。这不是 Root 权限；请仅批准你信任的应用。")
                Action("允许 Shell 权限", primary = true, enabled = !busy) { model.answerPermission(true) }
                Action("拒绝", enabled = !busy) { model.answerPermission(false) }
            }
        }
    }
}

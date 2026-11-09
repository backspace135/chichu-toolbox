package com.chichu.toolbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chichu.toolbox.core.common.ServiceState
import com.chichu.toolbox.core.common.ShellGrant
import com.chichu.toolbox.core.shizuku.adb.AdbEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

private val destinations = listOf("service" to "权限服务", "applications" to "应用授权", "diagnostics" to "设备诊断", "settings" to "设置")

@Composable
fun ToolboxApp(model: ToolboxViewModel) {
    val appearance by model.appearance.collectAsStateWithLifecycle()
    val mode = when (appearance.mode) {
        "light" -> if (appearance.dynamic) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        "dark" -> if (appearance.dynamic) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
        else -> if (appearance.dynamic) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }
    val controller = remember(mode) { ThemeController(colorSchemeMode = mode) }
    MiuixTheme(controller = controller) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "service"
        val icons = listOf(MiuixIcons.Home, MiuixIcons.GridView, MiuixIcons.Search, MiuixIcons.Settings)
        BoxWithConstraints(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).safeDrawingPadding().imePadding()) {
            val wide = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) NavigationRail(expanded = false) {
                    destinations.forEachIndexed { index, destination ->
                        NavigationRailItem(route == destination.first, { nav.select(destination.first) }, icons[index], destination.second)
                    }
                }
                Column(Modifier.weight(1f)) {
                    NavHost(nav, "service", Modifier.weight(1f)) {
                        composable("service") { ServiceScreen(model) { nav.select("applications") } }
                        composable("applications") { ApplicationsScreen(model) { nav.select("service") } }
                        composable("diagnostics") { DiagnosticsScreen(model) }
                        composable("settings") { SettingsScreen(model) }
                    }
                    if (!wide) NavigationBar(defaultWindowInsetsPadding = false) {
                        destinations.forEachIndexed { index, destination ->
                            NavigationBarItem(route == destination.first, { nav.select(destination.first) }, icons[index], destination.second)
                        }
                    }
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

private fun NavHostController.select(route: String) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
internal fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 800.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MiuixTheme.textStyles.title1, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
            content()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) Text(title, style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun Action(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        text = text, onClick = onClick, enabled = enabled, minHeight = 48.dp,
        modifier = Modifier.fillMaxWidth(),
        colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
    )
}

@Composable
internal fun Hint(text: String) = Text(text, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)

@Composable
private fun ServiceScreen(model: ToolboxViewModel, manage: () -> Unit) {
    val service by model.service.collectAsStateWithLifecycle()
    val activation by model.activation.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var wireless by rememberSaveable { mutableStateOf(false) }
    var computer by rememberSaveable { mutableStateOf(false) }
    var logs by rememberSaveable { mutableStateOf(false) }
    val presentation = service.presentation()
    Page("权限服务") {
        Group {
            Text(presentation.title, style = MiuixTheme.textStyles.title2, color = if (service is ServiceState.Connected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
            Hint(presentation.detail)
            if (activation.message.isNotEmpty()) Text(activation.message, style = MiuixTheme.textStyles.body2)
            if (service is ServiceState.Connected) Action("管理应用授权", primary = true, onClick = manage)
            else Action("无线调试激活", primary = true, enabled = !activation.busy) { wireless = true }
            if (activation.busy) Action("取消激活", onClick = model.activation::cancel)
        }
        if (model.originalInstalled) Group("共存提示") {
            Text("已安装原版 Shizuku", style = MiuixTheme.textStyles.headline2)
            Hint("尚未确认原版服务是否运行。工具箱不会自动停止或接管原版；第三方应用的连接归属仍需按实际连接顺序检查。")
        }
        Group("激活方式") {
            if (service is ServiceState.Connected) Action("无线调试", enabled = !activation.busy) { wireless = true }
            Action("电脑 ADB 激活") { computer = true }
            if (activation.log.isNotEmpty()) Action("查看启动日志") { logs = true }
            Hint("激活仅提供 Shell 权限，不是 Root。重启设备后需重新激活。")
        }
        Group("设备信息") {
            Text("${Build.MANUFACTURER} ${Build.MODEL}")
            Hint("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
        }
    }
    WindowBottomSheet(show = wireless, title = "无线调试激活", onDismissRequest = { wireless = false }) {
        WirelessContent(model) { openDeveloperSettings(context) }
    }
    WindowBottomSheet(show = computer, title = "电脑 ADB 激活", onDismissRequest = { computer = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Hint("1. 将手机连接电脑，启用 USB 调试，并确认电脑的调试授权。\n2. 在电脑终端执行下方命令。\n3. 回到工具箱，等待服务状态显示「已连接」。")
            SelectionContainer { Text(model.activation.computerCommand(), fontFamily = FontFamily.Monospace) }
            Action("复制当前激活命令", primary = true) { copyText(context, model.activation.computerCommand()) }
            Action("关闭") { computer = false }
        }
    }
    WindowBottomSheet(show = logs, title = "启动日志", onDismissRequest = { logs = false }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectionContainer { Text(activation.log, fontFamily = FontFamily.Monospace) }
            Action("复制日志") { copyText(context, activation.log) }
        }
    }
}

@Composable
private fun WirelessContent(model: ToolboxViewModel, openSettings: () -> Unit) {
    val status by model.activation.status.collectAsStateWithLifecycle()
    var pairingPort by rememberSaveable { mutableStateOf("") }
    // 配对码不保存至 saved state 或偏好设置。
    var code by remember { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var discovered by remember { mutableStateOf(emptyList<AdbEndpoint>()) }
    var discovering by remember { mutableStateOf(false) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        discovering = granted
        if (!granted) discoveryError = "未获本地网络权限，自动发现不可用"
    }
    LaunchedEffect(discovering) {
        if (!discovering) return@LaunchedEffect
        launch {
            try { model.discovery.discover(true).collect { found -> discovered = discovered.filterNot { it.pairing } + found }
            } catch (error: Exception) { discoveryError = error.message }
        }
        launch {
            try { model.discovery.discover(false).collect { found -> discovered = discovered.filter { it.pairing } + found }
            } catch (error: Exception) { discoveryError = error.message }
        }
    }
    Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("1. 开启无线调试", style = MiuixTheme.textStyles.headline2)
        Hint("在开发者选项中开启无线调试，并保持连接 Wi-Fi。首次使用需选择「使用配对码配对设备」。")
        Action("打开开发者选项", onClick = openSettings)
        Action(if (discovering) "停止自动发现" else "自动发现本机端口") {
            if (!discovering && Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
                permission.launch("android.permission.ACCESS_LOCAL_NETWORK")
            } else discovering = !discovering
        }
        discoveryError?.let { Hint("$it；仍可手动输入端口。") }
        discovered.forEach { endpoint ->
            Action("使用${if (endpoint.pairing) "配对" else "连接"}端口 ${endpoint.port}") {
                if (endpoint.pairing) pairingPort = endpoint.port.toString() else connectPort = endpoint.port.toString()
            }
        }
        Text("2. 配对设备", style = MiuixTheme.textStyles.headline2)
        Hint("配对端口来自六位配对码弹窗，与下一步的连接端口不同。已配对可直接跳到第 3 步。")
        PortField(pairingPort, { pairingPort = it }, "配对端口", !status.busy)
        TextField(value = code, onValueChange = { code = it }, label = "六位配对码", singleLine = true, enabled = !status.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Action("配对", enabled = !status.busy && pairingPort.isNotEmpty() && code.length == 6) {
            model.activation.pair(pairingPort, code)
            code = ""
        }
        Text("3. 连接并激活", style = MiuixTheme.textStyles.headline2)
        Hint("连接端口在无线调试主页面的「IP 地址和端口」中，不是配对码弹窗里的端口。")
        PortField(connectPort, { connectPort = it }, "连接端口", !status.busy)
        Action("启动内置服务", primary = true, enabled = !status.busy && connectPort.isNotEmpty()) { model.activation.activate(connectPort) }
        if (status.message.isNotEmpty()) Text(status.message)
        if (status.busy) Action("取消操作", onClick = model.activation::cancel)
    }
}

@Composable
private fun PortField(value: String, change: (String) -> Unit, label: String, enabled: Boolean) {
    TextField(value = value, onValueChange = change, label = label, singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun ApplicationsScreen(model: ToolboxViewModel, activate: () -> Unit) {
    val state by model.applications.collectAsStateWithLifecycle()
    val service by model.service.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<ApplicationItem?>(null) }
    Page("应用授权") {
        if (service !is ServiceState.Connected) Group {
            Text("请先激活权限服务", style = MiuixTheme.textStyles.headline2)
            Hint("服务离线时无法读取或修改 Shell 授权，当前状态不表示没有应用。")
            Action("前往激活", primary = true, onClick = activate)
        } else {
            TextField(value = query, onValueChange = { query = it }, label = "搜索应用名或包名", singleLine = true, modifier = Modifier.fillMaxWidth())
            Hint("此列表仅管理 Shell 授权。同一 UID 下的应用共享授权状态。")
            when {
                state.loading -> Group { Text("正在加载应用…") }
                state.error != null -> Group { Text(state.error!!); Action("重新加载", onClick = model::refresh) }
                state.items.isEmpty() -> Group { Text("暂无兼容应用"); Hint("安装支持 Shizuku API 的应用后，点击刷新。") }
                state.items.matching(query).isEmpty() -> Group { Text("没有匹配的应用"); Hint("尝试其他应用名或包名。") }
                else -> state.items.matching(query).forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp), onClick = { selected = item }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(item.packageName)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.label, style = MiuixTheme.textStyles.headline2)
                                Hint(item.packageName)
                                Text("${item.grant.label} · UID ${item.uid}", color = if (item.grant == ShellGrant.Allowed) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
            Action("刷新应用列表", enabled = !state.loading, onClick = model::refresh)
        }
    }
    val item = selected
    WindowDialog(show = item != null && service is ServiceState.Connected, title = item?.label, summary = "管理 Shell 权限（不是 Root）。同 UID 应用会一并生效。", onDismissRequest = { selected = null }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item?.let {
                Hint(it.packageName)
                Action("允许", primary = true, enabled = state.changingUid == null) { model.setGrant(it, ShellGrant.Allowed); selected = null }
                Action("拒绝", enabled = state.changingUid == null) { model.setGrant(it, ShellGrant.Denied); selected = null }
                Action("撤销并恢复询问", enabled = state.changingUid == null) { model.setGrant(it, ShellGrant.Ask); selected = null }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(44.dp)) }
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
                AppIcon(it.packageName)
                Text(it.packageName, style = MiuixTheme.textStyles.headline2)
                Hint("UID ${it.uid} · 身份已由内置服务验证\n允许后，此应用可通过 Shell 操作设备。这不是 Root 权限；请仅批准你信任的应用。")
                Action("允许", primary = true, enabled = !busy) { model.answerPermission(true) }
                Action("拒绝", enabled = !busy) { model.answerPermission(false) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(model: ToolboxViewModel) {
    val appearance by model.appearance.collectAsStateWithLifecycle()
    var chooseTheme by rememberSaveable { mutableStateOf(false) }
    var licenses by rememberSaveable { mutableStateOf(false) }
    val themeName = when (appearance.mode) { "light" -> "浅色"; "dark" -> "深色"; else -> "跟随系统" }
    Page("设置") {
        Text("外观", style = MiuixTheme.textStyles.subtitle, modifier = Modifier.padding(start = 12.dp))
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(title = "主题", summary = themeName, onClick = { chooseTheme = true })
            SwitchPreference(checked = appearance.dynamic, onCheckedChange = { model.setAppearance(appearance.copy(dynamic = it)) }, title = "动态配色", summary = "使用当前壁纸的系统配色")
        }
        Group("服务说明") {
            Text("Shell 与 Root 相互独立", style = MiuixTheme.textStyles.headline2)
            Hint("权限服务提供通过 ADB 激活的 Shell 权限；设备诊断只读取设备信息与配置文本。Shell 授权不授予 Root。")
        }
        Card(Modifier.fillMaxWidth()) {
            ArrowPreference(title = "彳亍工具箱", summary = "${BuildConfig.VERSION_NAME} · M2 开发中")
            ArrowPreference(title = "开源许可证", summary = "Shizuku、Shizuku API、miuix 与依赖来源", onClick = { licenses = true })
        }
    }
    WindowDialog(show = chooseTheme, title = "主题", onDismissRequest = { chooseTheme = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, title) ->
                Action(title, primary = appearance.mode == mode) { model.setAppearance(appearance.copy(mode = mode)); chooseTheme = false }
            }
        }
    }
    WindowBottomSheet(show = licenses, title = "开源许可证", onDismissRequest = { licenses = false }) {
        val context = LocalContext.current
        var notice by remember { mutableStateOf("正在读取许可证…") }
        LaunchedEffect(Unit) { notice = withContext(Dispatchers.IO) { context.assets.open("OPEN_SOURCE_NOTICES.txt").bufferedReader().use { it.readText() } } }
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
            SelectionContainer { Text(notice, style = MiuixTheme.textStyles.body2) }
        }
    }
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("彳亍工具箱", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun openDeveloperSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        .onFailure { Toast.makeText(context, "无法打开开发者选项，请在系统设置中手动打开", Toast.LENGTH_LONG).show() }
}

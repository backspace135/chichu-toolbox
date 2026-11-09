package com.chichu.toolbox

import android.content.ClipData
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chichu.toolbox.core.shizuku.adb.AdbEndpoint
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ActivationSheets(model: ToolboxViewModel, sheet: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activation by model.activation.status.collectAsStateWithLifecycle()
    DetailSheet(sheet == "wireless", "无线调试激活", onDismiss) {
        WirelessContent(model) { openDeveloperSettings(context) }
    }
    DetailSheet(sheet == "computer", "电脑 ADB 激活", onDismiss) {
        Text("1. 连接电脑", style = MiuixTheme.textStyles.headline2)
        Hint("启用 USB 调试，并在手机上确认电脑的调试授权。")
        Text("2. 执行激活命令", style = MiuixTheme.textStyles.headline2)
        SelectionContainer { Text(model.activation.computerCommand(), fontFamily = FontFamily.Monospace, style = MiuixTheme.textStyles.body2) }
        Action("复制当前激活命令", primary = true) { copyText(context, model.activation.computerCommand()) }
        Text("3. 返回工具箱", style = MiuixTheme.textStyles.headline2)
        Hint("等待服务显示「已连接」。命令执行成功不代表已收到内置服务连接。")
    }
    DetailSheet(sheet == "logs", "启动日志", onDismiss) {
        SelectionContainer { Text(activation.log, fontFamily = FontFamily.Monospace, style = MiuixTheme.textStyles.body2) }
        Action("复制日志") { copyText(context, activation.log) }
    }
}

@Composable
private fun WirelessContent(model: ToolboxViewModel, openSettings: () -> Unit) {
    val status by model.activation.status.collectAsStateWithLifecycle()
    var pairingPort by rememberSaveable { mutableStateOf("") }
    // Pairing codes stay in this operation's memory only, never saved state.
    var code by remember { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var discovered by remember { mutableStateOf(emptyList<AdbEndpoint>()) }
    var discovering by remember { mutableStateOf(false) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var notificationError by remember { mutableStateOf<String?>(null) }
    var requestNotifications by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun startNotificationPairing() {
        if (!PairingNotificationService.notificationsEnabled(context)) {
            notificationError = "请先允许无线调试配对通知；仍可使用下方手动配对。"
            return
        }
        runCatching { PairingNotificationService.start(context) }
            .onSuccess { notificationError = "通知配对已开启。打开系统配对码窗口后，从通知中输入配对码。" }
            .onFailure { notificationError = "无法启动通知配对，请重试或使用下方手动配对。" }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startNotificationPairing() else notificationError = "未获通知权限，仍可使用下方手动配对。"
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val startNotification = requestNotifications
        requestNotifications = false
        if (granted && startNotification) {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startNotificationPairing()
        } else discovering = granted
        if (!granted) discoveryError = "未获本地网络权限，自动发现不可用"
    }
    LaunchedEffect(discovering) {
        if (!discovering) return@LaunchedEffect
        launch {
            try { model.discovery.discover(true).collect { found -> discovered = discovered.filterNot { it.pairing } + found }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (error: Exception) { discoveryError = error.message }
        }
        launch {
            try { model.discovery.discover(false).collect { found -> discovered = discovered.filter { it.pairing } + found }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (error: Exception) { discoveryError = error.message }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("通知栏配对", style = MiuixTheme.textStyles.headline2)
        Hint("像 Shizuku 一样，在系统配对码窗口保持打开时，直接从通知中输入六位配对码，无需来回切换。")
        if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
            Hint("MIUI 若点「输入配对码」没有输入框，请在系统「通知与控制中心 → 通知显示设置」切换为原生通知样式，或使用下方手动配对。")
        }
        Action("开启通知栏配对", primary = true, enabled = !status.busy) {
            discovering = false
            if (Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
                requestNotifications = true
                permission.launch("android.permission.ACCESS_LOCAL_NETWORK")
            } else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else startNotificationPairing()
        }
        notificationError?.let { Hint(it) }
        if (!PairingNotificationService.notificationsEnabled(context)) Action("打开配对通知设置") {
            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, "wireless_pairing"))
        }
        Action("停止通知栏配对") { PairingNotificationService.stop(context); notificationError = "通知栏配对已停止。" }
        Text("1. 开启无线调试", style = MiuixTheme.textStyles.headline2)
        Hint("在开发者选项中开启无线调试，并保持连接 Wi-Fi。首次使用需选择「使用配对码配对设备」。")
        Action("打开开发者选项", onClick = openSettings)
        Action(if (discovering) "停止自动发现" else "自动发现本机端口") {
            requestNotifications = false
            discoveryError = null
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
        Text("2. 配对设备", style = MiuixTheme.textStyles.headline2, modifier = Modifier.padding(top = 8.dp))
        Hint("配对端口来自六位配对码弹窗，与下一步的连接端口不同。已配对可直接跳到第 3 步。")
        PortField(pairingPort, { pairingPort = it }, "配对端口", !status.busy)
        TextField(value = code, onValueChange = { code = it }, label = "六位配对码", singleLine = true, enabled = !status.busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Action("配对", enabled = !status.busy && pairingPort.isNotEmpty() && code.length == 6) {
            model.activation.pair(pairingPort, code)
            code = ""
        }
        Text("3. 连接并激活", style = MiuixTheme.textStyles.headline2, modifier = Modifier.padding(top = 8.dp))
        Hint("连接端口在无线调试主页面的「IP 地址和端口」中，不是配对码弹窗里的端口。")
        PortField(connectPort, { connectPort = it }, "连接端口", !status.busy)
        Action("启动内置服务", primary = true, enabled = !status.busy && connectPort.isNotEmpty()) { model.activation.activate(connectPort) }
        if (status.message.isNotEmpty()) Text(status.message, style = MiuixTheme.textStyles.body2)
        if (status.busy) Action("取消操作", onClick = model.activation::cancel)
    }
}

@Composable
private fun PortField(value: String, change: (String) -> Unit, label: String, enabled: Boolean) {
    TextField(value = value, onValueChange = change, label = label, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

internal fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("彳亍工具箱", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun openDeveloperSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        .onFailure { Toast.makeText(context, "无法打开开发者选项，请在系统设置中手动打开", Toast.LENGTH_LONG).show() }
}

package com.chichu.toolbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun SettingsScreen(model: ToolboxViewModel) {
    val appearance by model.appearance.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val themeName = when (appearance.mode) { "light" -> "浅色"; "dark" -> "深色"; else -> "跟随系统" }
    Page("设置", onAction = { sheet = "permissions" }) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(ToolboxStyle.cardRadius)).background(ToolboxStyle.brandColor),
            )
            Text("彳亍工具箱", style = MiuixTheme.textStyles.headline2)
            Hint("${BuildConfig.VERSION_NAME} · Android")
        }
        SectionLabel("外观")
        ListCard {
            PreferenceRow("主题", themeName, MiuixIcons.Theme) { sheet = "theme" }
            ListDivider()
            SwitchPreference(
                checked = appearance.dynamic, onCheckedChange = { model.setAppearance(appearance.copy(dynamic = it)) },
                title = "动态配色", summary = "使用当前壁纸的系统配色",
                startAction = { top.yukonga.miuix.kmp.basic.Icon(MiuixIcons.Tune, contentDescription = null, modifier = Modifier.padding(end = 12.dp).size(24.dp)) },
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
            )
        }
        SectionLabel("服务")
        ListCard {
            PreferenceRow("Shell 与 Root", "了解两种权限的区别", MiuixIcons.Lock) { sheet = "permissions" }
            ListDivider()
            PreferenceRow("与原版 Shizuku 共存", "独立服务与独立授权", MiuixIcons.Link) { sheet = "coexist" }
        }
        SectionLabel("关于")
        ListCard {
            PreferenceRow("彳亍工具箱", "${BuildConfig.VERSION_NAME} · M2 开发中", MiuixIcons.Info) { sheet = "about" }
            ListDivider()
            PreferenceRow("开源许可证", "Shizuku、miuix 与其他依赖", MiuixIcons.File) { sheet = "licenses" }
        }
    }
    WindowDialog(show = sheet == "theme", title = "主题", largeScreen = true, onDismissRequest = { sheet = null }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, title) ->
                Action(if (appearance.mode == mode) "$title（当前）" else title, primary = appearance.mode == mode) {
                    model.setAppearance(appearance.copy(mode = mode)); sheet = null
                }
            }
        }
    }
    DetailSheet(sheet == "permissions", "Shell 与 Root", { sheet = null }) {
        Text("Shell 权限", style = MiuixTheme.textStyles.headline2)
        Hint("通过 ADB 激活内置服务，运行身份为 UID 2000。应用通过 Shizuku API 使用经批准的 Shell 权限。")
        Text("Root 权限", style = MiuixTheme.textStyles.headline2)
        Hint("独立的设备能力，需要单独验证和管理。Shell 授权不会授予 Root；设备诊断与配置文本校验不能证明 Root 可用。")
    }
    CoexistSheet(sheet == "coexist", model.originalInstalled) { sheet = null }
    DetailSheet(sheet == "about", "关于彳亍工具箱", { sheet = null }) {
        Text(BuildConfig.VERSION_NAME, style = MiuixTheme.textStyles.headline2)
        Hint("M1 内置 Shell 权限服务已交付；M2 开发中，当前提供只读设备诊断与本地内核配置文本校验。")
    }
    DetailSheet(sheet == "licenses", "开源许可证", { sheet = null }) {
        val context = LocalContext.current
        var notice by remember { mutableStateOf("正在读取许可证…") }
        LaunchedEffect(Unit) { notice = withContext(Dispatchers.IO) { context.assets.open("OPEN_SOURCE_NOTICES.txt").bufferedReader().use { it.readText() } } }
        SelectionContainer { Text(notice, style = MiuixTheme.textStyles.body2) }
    }
}

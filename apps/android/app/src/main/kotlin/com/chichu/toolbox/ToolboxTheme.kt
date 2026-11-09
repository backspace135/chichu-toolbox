package com.chichu.toolbox

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Native implementation of docs/android/UI_DESIGN.md. */
internal object ToolboxStyle {
    val pageMargin = 12.dp
    val contentWidth = 800.dp
    val cardRadius = 20.dp
    val statusRadius = 24.dp
    val buttonRadius = 14.dp
    val searchRadius = 28.dp
    val filterRadius = 12.dp
    val tagRadius = 8.dp
    val iconRadius = 14.dp
    val brandColor = Color(0xFF3155BC)

    val connectedContainer: Color
        @Composable get() = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.tertiaryContainer
        else if (MiuixTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF193B28) else Color(0xFFE0F5E7)

    val connectedContent: Color
        @Composable get() = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.onTertiaryContainer
        else if (MiuixTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFA0DBB4) else Color(0xFF237742)
}

@Composable
internal fun ToolboxTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val controller = remember(appearance) {
        val mode = when (appearance.mode) {
            "light" -> if (appearance.dynamic) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
            "dark" -> if (appearance.dynamic) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
            else -> if (appearance.dynamic) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        }
        ThemeController(
            colorSchemeMode = mode,
            lightColors = lightColorScheme(
                primary = Color(0xFF3482FF), onPrimary = Color.White,
                primaryContainer = Color(0xFFEAF2FF), onPrimaryContainer = Color(0xFF3482FF),
                tertiaryContainer = Color(0xFFEAF2FF), onTertiaryContainer = Color(0xFF3482FF),
                background = Color(0xFFF6F6F6), surface = Color(0xFFF6F6F6),
                onBackground = Color(0xFF17191C), onSurface = Color(0xFF17191C),
                onBackgroundVariant = Color(0xFF7E879F),
                surfaceContainer = Color.White, surfaceVariant = Color.White,
                onSurfaceContainer = Color(0xFF17191C),
                onSurfaceVariantSummary = Color(0xFF777B83), onSurfaceVariantActions = Color(0xFF777B83),
                secondaryVariant = Color(0xFFEAEAEC), onSecondaryVariant = Color(0xFF17191C),
                dividerLine = Color(0xFFF0F0F2),
            ),
            darkColors = darkColorScheme(
                primary = Color(0xFF65A1FF), onPrimary = Color(0xFF071833),
                primaryContainer = Color(0xFF21334F), onPrimaryContainer = Color(0xFF65A1FF),
                tertiaryContainer = Color(0xFF21334F), onTertiaryContainer = Color(0xFF65A1FF),
                background = Color.Black, surface = Color.Black,
                onBackground = Color(0xFFF0F0F0), onSurface = Color(0xFFF0F0F0),
                onBackgroundVariant = Color(0xFFA2A6B8),
                surfaceContainer = Color(0xFF232323), surfaceVariant = Color(0xFF232323),
                onSurfaceContainer = Color(0xFFF0F0F0),
                onSurfaceVariantSummary = Color(0xFFA4A4AA), onSurfaceVariantActions = Color(0xFFA4A4AA),
                secondaryVariant = Color(0xFF2A2A2C), onSecondaryVariant = Color(0xFFF0F0F0),
                dividerLine = Color(0xFF323234),
            ),
        )
    }
    val textStyles = remember {
        defaultTextStyles().let { defaults ->
            defaults.copy(
                title1 = defaults.title1.copy(fontSize = 30.sp, fontWeight = FontWeight.Medium),
                title2 = defaults.title2.copy(fontSize = 25.sp, fontWeight = FontWeight.SemiBold),
                title3 = defaults.title3.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
                headline2 = defaults.headline2.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                body2 = defaults.body2.copy(fontSize = 13.sp, lineHeight = 20.sp),
                subtitle = defaults.subtitle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
    MiuixTheme(controller = controller, textStyles = textStyles) {
        val activity = LocalActivity.current as? ComponentActivity
        val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
        LaunchedEffect(activity, dark) {
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            )
        }
        content()
    }
}

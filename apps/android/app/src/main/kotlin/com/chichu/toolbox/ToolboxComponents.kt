package com.chichu.toolbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun Page(
    title: String,
    actionIcon: ImageVector = MiuixIcons.Info,
    actionLabel: String = "查看页面说明",
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyPage(title, actionIcon, actionLabel, onAction) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
internal fun LazyPage(
    title: String,
    actionIcon: ImageVector = MiuixIcons.Info,
    actionLabel: String = "查看页面说明",
    onAction: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxStyle.contentWidth).fillMaxSize(),
            contentPadding = PaddingValues(start = ToolboxStyle.pageMargin, end = ToolboxStyle.pageMargin, bottom = 24.dp),
        ) {
            item {
                Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 24.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("彳亍工具箱", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                        if (onAction != null) IconButton(onClick = onAction, modifier = Modifier.size(48.dp)) {
                            Icon(actionIcon, contentDescription = actionLabel, modifier = Modifier.size(24.dp))
                        } else Spacer(Modifier.height(48.dp))
                    }
                    Text(title, style = MiuixTheme.textStyles.title1, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                }
            }
            content()
        }
    }
}

@Composable
internal fun SectionLabel(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.let { Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
    }
}

@Composable
internal fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) SectionLabel(title)
    ListCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), cornerRadius = ToolboxStyle.cardRadius, content = content)
}

@Composable
internal fun PreferenceRow(title: String, summary: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    ArrowPreference(
        title = title, summary = summary, enabled = enabled, onClick = onClick,
        modifier = Modifier.heightIn(min = 74.dp), insideMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
        startAction = { Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 12.dp).size(24.dp)) },
    )
}

@Composable
internal fun ListDivider(inset: Dp = 52.dp) {
    HorizontalDivider(modifier = Modifier.padding(start = inset, end = 16.dp))
}

@Composable
internal fun RowScope.ToolboxNavigationItem(selected: Boolean, onClick: () -> Unit, icon: ImageVector, label: String) {
    val color = if (selected) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Column(
        Modifier.weight(1f).heightIn(min = 64.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(
            if (largeText && label.length == 4) label.chunked(2).joinToString("\n") else label,
            color = color, fontSize = 12.sp, textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            minLines = if (largeText) 2 else 1,
        )
    }
}

@Composable
internal fun Action(text: String, primary: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, minHeight = 48.dp, cornerRadius = ToolboxStyle.buttonRadius,
        modifier = Modifier.fillMaxWidth(),
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MiuixTheme.textStyles.button)
    }
}

@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) = Text(
    text, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = modifier,
)

@Composable
internal fun Footnote(text: String) = Hint(text, Modifier.padding(horizontal = 12.dp))

@Composable
internal fun DetailSheet(show: Boolean, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    val insets = WindowInsets.safeDrawing
    val systemBarsHeight = with(density) { (insets.getTop(this) + insets.getBottom(this)).toDp() }
    // Reserve room for the sheet handle/title and real system bars before measuring
    // the scroll viewport. The library's wrap-content sheet can otherwise extend below them.
    val contentHeight = (LocalWindowInfo.current.containerDpSize.height - systemBarsHeight - 160.dp * fontScale).coerceIn(48.dp, 600.dp)
    WindowBottomSheet(
        show = show, title = title, onDismissRequest = onDismiss,
        endAction = {
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(MiuixIcons.Close, contentDescription = "关闭弹层", modifier = Modifier.size(24.dp))
            }
        },
    ) {
        Column(
            Modifier.heightIn(max = contentHeight)
                .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
        )
    }
}

/** Small functional icons matching the approved mock; other icons come from miuix. */
internal object ToolboxIcons {
    private fun lineIcon(name: String, block: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
    }.build()

    val Wireless = lineIcon("Wireless") {
        moveTo(2f, 8f); curveTo(8f, 3.3f, 16f, 3.3f, 22f, 8f)
        moveTo(5f, 12f); curveTo(9f, 8.8f, 15f, 8.8f, 19f, 12f)
        moveTo(8.5f, 16f); curveTo(10.5f, 14.4f, 13.5f, 14.4f, 15.5f, 16f)
        moveTo(12f, 20f); lineTo(12.01f, 20f)
    }
    val Terminal = lineIcon("Terminal") {
        moveTo(5f, 4f); lineTo(19f, 4f); quadTo(22f, 4f, 22f, 7f); lineTo(22f, 17f); quadTo(22f, 20f, 19f, 20f)
        lineTo(5f, 20f); quadTo(2f, 20f, 2f, 17f); lineTo(2f, 7f); quadTo(2f, 4f, 5f, 4f); close()
        moveTo(7f, 9f); lineTo(10f, 12f); lineTo(7f, 15f); moveTo(13f, 15f); lineTo(17f, 15f)
    }
    val Device = lineIcon("Device") {
        moveTo(8f, 2f); lineTo(16f, 2f); quadTo(18f, 2f, 18f, 4f); lineTo(18f, 20f); quadTo(18f, 22f, 16f, 22f)
        lineTo(8f, 22f); quadTo(6f, 22f, 6f, 20f); lineTo(6f, 4f); quadTo(6f, 2f, 8f, 2f); close()
        moveTo(10f, 5f); lineTo(14f, 5f); moveTo(11f, 19f); lineTo(13f, 19f)
    }
    val Diagnostics = lineIcon("Diagnostics") {
        moveTo(7f, 5f); lineTo(17f, 5f); quadTo(19f, 5f, 19f, 7f); lineTo(19f, 17f); quadTo(19f, 19f, 17f, 19f)
        lineTo(7f, 19f); quadTo(5f, 19f, 5f, 17f); lineTo(5f, 7f); quadTo(5f, 5f, 7f, 5f); close()
        moveTo(9f, 9f); lineTo(15f, 9f); lineTo(15f, 15f); lineTo(9f, 15f); close()
        moveTo(9f, 2f); lineTo(9f, 5f); moveTo(15f, 2f); lineTo(15f, 5f)
        moveTo(9f, 19f); lineTo(9f, 22f); moveTo(15f, 19f); lineTo(15f, 22f)
        moveTo(2f, 9f); lineTo(5f, 9f); moveTo(2f, 15f); lineTo(5f, 15f)
        moveTo(19f, 9f); lineTo(22f, 9f); moveTo(19f, 15f); lineTo(22f, 15f)
    }
    val CheckCircle = lineIcon("CheckCircle") {
        moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f)
        moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
    }
}

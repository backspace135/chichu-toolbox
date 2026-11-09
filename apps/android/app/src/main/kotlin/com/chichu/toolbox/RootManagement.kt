package com.chichu.toolbox

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chichu.toolbox.core.ksu.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun rememberRootModel(model: ToolboxViewModel): RootViewModel = viewModel {
    RootViewModel(model.rootRepository)
}

@Composable
internal fun RootManagement(show: Boolean, model: RootViewModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("status") }
    var selected by remember { mutableStateOf<RootApplication?>(null) }
    var webModule by remember { mutableStateOf<RootModule?>(null) }
    var approvedWeb by remember { mutableStateOf<RootModule?>(null) }
    var confirmation by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var migration by remember { mutableStateOf(false) }
    var resultExpanded by remember { mutableStateOf(false) }
    val install = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::install) }
    LaunchedEffect(show) { if (show) { section = "status"; model.refresh() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (show) model.revalidate() }
    val close = {
        when {
            approvedWeb != null -> approvedWeb = null
            selected != null -> selected = null
            section != "status" -> section = "status"
            else -> onDismiss()
        }
    }
    BackHandler(show, close)
    DetailSheet(show, when (section) {
        "apps" -> "超级用户与应用策略"
        "modules" -> "KernelSU 模块"
        "maintenance" -> "安装与维护"
        "identity" -> "管理器身份与迁移"
        else -> "Root 管理"
    }, close) {
        if (state.loading) Hint("正在读取内核与管理器身份…")
        if (state.busy) Hint("正在执行，请等待实际结果…")
        state.error?.let { Text(it, color = MiuixTheme.colorScheme.error); Hint("请检查下方状态或日志，刷新后重试。") }
        state.result?.let { result ->
            Hint(result.lineSequence().first())
            Action(if (resultExpanded) "收起执行结果" else "查看完整执行结果") { resultExpanded = !resultExpanded }
            if (resultExpanded) SelectionContainer { Text(result, fontFamily = FontFamily.Monospace, style = MiuixTheme.textStyles.body2) }
        }
        if (state.snapshot == null && !state.loading) Action("重新探测", enabled = !state.busy, onClick = model::refresh)
        val snapshot = state.snapshot
        if (snapshot != null) {
            when (section) {
                "status" -> {
                    Text(when {
                        snapshot.canManage -> "管理通道已验证"
                        snapshot.canModules -> "已有 Root · 模块通道已验证"
                        else -> "管理通道未就绪"
                    }, style = MiuixTheme.textStyles.headline2)
                    val running = snapshot.runningKernel
                    Hint("KernelSU ${if (running.available) running.version else "未确认"} · ${if (running.available) running.mode.title else "运行模式未确认"} · UAPI ${running.uapi}")
                    snapshot.issue?.let { Hint(it) }
                    Hint("${snapshot.daemonVersion}\nShell 授权与 Root 授权相互独立。")
                    snapshot.safeMode?.let { Hint(if (it) "KernelSU 安全模式已开启，模块可能不运行。" else "KernelSU 安全模式：关闭") }
                    Action("验证 KernelSU Root 通道", primary = true, enabled = !state.busy && snapshot.kernel.managementIssue == null && snapshot.daemonAvailable) { model.verify() }
                    Action("检查已有 Root（请求 su）", enabled = !state.busy) { model.verify(existing = true) }
                    ListCard {
                        PreferenceRow("超级用户与应用策略", "${if (state.loaded) "${state.applications.count { it.profile.allowed }} 个 UID/应用已允许" else "需先验证管理通道"}", MiuixIcons.Lock, snapshot.canManage && !state.busy) { section = "apps" }
                        ListDivider()
                        PreferenceRow("模块管理", "安装、更新、启停、操作与 WebUI", MiuixIcons.GridView, snapshot.canModules && !state.busy) { section = "modules" }
                        ListDivider()
                        PreferenceRow("安装与维护", "按当前内核模式开放", ToolboxIcons.Terminal, snapshot.canModules && !state.busy) { section = "maintenance" }
                        ListDivider()
                        PreferenceRow("管理器身份与迁移", if (snapshot.originalInstalled) "检测到原版 KernelSU Manager" else "包名、签名与接管条件", MiuixIcons.Info) { section = "identity" }
                    }
                    Action("操作记录与故障日志", enabled = !state.busy) { model.logs() }
                    Action("内核 Root 调用日志", enabled = snapshot.canModules && !state.busy) { model.logs(kernel = true) }
                    Action("重新探测", enabled = !state.busy, onClick = model::refresh)
                }
                "apps" -> {
                    Hint("KernelSU 使用持久化允许列表，没有 Shell 的「待询问」状态。撤销会将后续 Root 请求设为不允许；同 UID 应用共享授权。")
                    var search by rememberSaveable { mutableStateOf("") }
                    TextField(search, { search = it }, label = "按应用名或包名搜索", modifier = Modifier.fillMaxWidth())
                    if (!state.loaded) Hint("应用列表尚未加载，请返回并验证管理通道。")
                    if (state.loaded && state.applications.isEmpty()) Hint("当前用户没有可管理应用。")
                    val apps = state.applications.filter { it.name.contains(search, true) || it.packageName.contains(search, true) }
                    if (state.loaded && apps.isEmpty() && search.isNotEmpty()) Hint("没有匹配的应用。")
                    ListCard {
                        LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                            items(apps, key = { it.packageName }) { app ->
                                PreferenceRow(app.name, "${app.packageName}\nUID ${app.uid} · ${if (app.profile.allowed) "已允许 Root" else "不允许 Root"}", MiuixIcons.Lock, !state.busy) { selected = app }
                                ListDivider()
                            }
                        }
                    }
                    Hint("模板在应用策略弹层中保存与应用；保存在工具箱私有存储，应用模板后必须经过内核读回。")
                }
                "modules" -> {
                    Hint("选择 ZIP 可安装新模块或更新已有模块；结果以 ksud 退出码和输出为准。")
                    Action("选择模块 ZIP（安装 / 更新）", primary = true, enabled = !state.busy) { install.launch(arrayOf("application/zip", "application/octet-stream")) }
                    if (state.loaded && state.modules.isEmpty()) Hint("ksud 返回当前没有模块。")
                    state.modules.forEach { module ->
                        Group(module.name) {
                            Text(module.id, fontFamily = FontFamily.Monospace)
                            Hint("${module.version} · ${if (module.enabled) "启用" else "禁用"}${if (module.update) " · 待更新" else ""}${if (module.remove) " · 待卸载" else ""}\n${module.description}")
                            Action(if (module.enabled) "禁用模块" else "启用模块", enabled = !state.busy && !module.remove) { model.moduleAction(module, if (module.enabled) "disable" else "enable") }
                            Action(if (module.remove) "撤销卸载" else "卸载模块", enabled = !state.busy) {
                                if (module.remove) model.moduleAction(module, "undo-uninstall")
                                else confirmation = "卸载 ${module.name}；可能在重启后生效。" to listOf("module", "uninstall", module.id)
                            }
                            if (module.action) Action("执行模块操作", enabled = !state.busy && !module.remove) {
                                confirmation = "以 Root 执行 ${module.name} 的 action.sh。" to listOf("module", "action", module.id)
                            }
                            if (module.web) Action("打开模块 WebUI", enabled = !state.busy && !module.remove) { webModule = module }
                        }
                    }
                }
                "maintenance" -> {
                    val mode = snapshot.runningKernel.mode
                    Hint("当前模式：${mode.title}。所有操作执行前确认，结果保留在日志。")
                    Action("安装 / 更新 ksud 用户空间", enabled = snapshot.canManage && !state.busy) {
                        confirmation = "将固定版本 ksud 安装到 /data/adb/ksu；可能与现有管理器使用同一用户空间。" to listOf("install")
                    }
                    if (!snapshot.canManage) Hint("当前为原版管理器授权的已有 Root 模块通道；不替换其用户空间组件。")
                    val persistent = mode == RootMode.Lkm || mode == RootMode.Bundled
                    if (!persistent) Hint("${mode.title} 不提供直接刷写或恢复启动镜像入口；临时加载不会伪装为持久安装。")
                    Action("直接安装 / 更新启动镜像", enabled = false) {}
                    Hint("当前固定资源包含原版签名绑定的 LKM，不是工具箱配套内核。为避免刷写后丢失管理身份，镜像安装 / 修补暂不可用；需先生成工具箱签名绑定资源。")
                    Action("恢复启动镜像", enabled = persistent && !state.busy) {
                        confirmation = "从 KernelSU 备份恢复并刷写启动分区；备份不存在时命令将失败。" to listOf("boot-restore", "--flash")
                    }
                    Action("检查内核功能设置", enabled = !state.busy) { model.execute(listOf("feature", "list")) }
                }
                "identity" -> {
                    Hint("${if (snapshot.originalInstalled) "已安装" else "未检测到"}原版 me.weishu.kernelsu。安装状态不代表其管理关系。工具箱不会自动修改原版授权、停止原版或夺取管理器身份。")
                    Hint("工具箱包名：com.chichu.toolbox\n内核管理器标志：${snapshot.kernel.manager}\n内核 UAPI ${snapshot.kernel.uapi} / 工具箱 UAPI $KSU_UAPI")
                    Hint("工具箱 APK 签名证书 SHA-256（内核核对 v2 块中的证书长度与此摘要，还要求没有 v3 / v3.1 块）")
                    SelectionContainer { Text(snapshot.certificateSha256, fontFamily = FontFamily.Monospace, style = MiuixTheme.textStyles.body2) }
                    Hint("正式内核必须在构建时绑定工具箱包名与 APK 签名；stock KernelSU 绑定原版身份，不能只靠改包名接管。M2 临时加载也必须使用配套内核资源。")
                    Hint("debug 内核仅在 CONFIG_KSU_DEBUG 管理器参数可写且用户已有 Root 授权时可迁移。迁移改变当前管理 UID，会使原版失去管理身份；内核策略与模块不复制、不删除。")
                    Action("请求迁移 debug 内核管理器…", enabled = !state.busy && snapshot.daemonAvailable && !snapshot.kernel.manager) { migration = true }
                    Hint("固定源码：$KSU_SOURCE")
                }
            }
        }
    }
    selected?.let { app -> PolicyDialog(app, state.busy, state.templates, model::saveTemplate, model::deleteTemplate, { selected = null }) { policy -> model.setPolicy(app, policy); selected = null } }
    val pending = confirmation
    WindowDialog(show = pending != null, title = "确认 Root 操作", summary = pending?.first, onDismissRequest = { confirmation = null }) {
        Action("确认执行", enabled = !state.busy) { pending?.let { model.execute(it.second, reload = section == "modules") }; confirmation = null }
        Action("取消") { confirmation = null }
    }
    WindowDialog(show = migration, title = "确认管理器迁移", summary = "仅适用于允许迁移的 debug 内核。成功时工具箱会被 ksud 关闭，重新打开后必须验证身份。原版管理器将失去当前管理身份。", onDismissRequest = { migration = false }) {
        Action("确认迁移", enabled = !state.busy) { migration = false; model.migrate() }
        Action("取消") { migration = false }
    }
    WindowDialog(show = webModule != null, title = "允许模块 WebUI 使用 Root？", summary = "${webModule?.name.orEmpty()} 的本地页面将可执行 Root 命令。仅对本次打开授权；远程页面、子框架和外部脚本不获得桥接。", onDismissRequest = { webModule = null }) {
        Action("信任并打开", enabled = !state.busy) { approvedWeb = webModule; webModule = null }
        Action("取消") { webModule = null }
    }
    approvedWeb?.let { module -> ModuleWebUi(module, model.repository) { approvedWeb = null } }
}

@Composable
private fun PolicyDialog(app: RootApplication, busy: Boolean, templates: Map<String, PolicyTemplate>,
    saveTemplate: (String, String) -> Unit, deleteTemplate: (String) -> Unit, close: () -> Unit, save: (AppPolicy) -> Unit) {
    var uid by remember(app.uid) { mutableStateOf(if (app.profile.allowed && !app.profile.useDefault) app.profile.uid.toString() else "0") }
    var gid by remember(app.uid) { mutableStateOf(if (app.profile.allowed && !app.profile.useDefault) app.profile.gid.toString() else "0") }
    var namespace by remember(app.uid) { mutableIntStateOf(if (app.profile.allowed && !app.profile.useDefault) app.profile.namespace else 0) }
    val initial = remember(app) {
        if (app.profile.allowed && !app.profile.useDefault) app.profile else app.profile.custom(0, 0, 0)
    }
    var templateText by remember(app) { mutableStateOf(initial.template().serialize()) }
    var templateName by remember(app) { mutableStateOf("") }
    var policyError by remember { mutableStateOf<String?>(null) }
    DetailSheet(true, app.name, close) {
        Hint("${app.packageName}\nUID ${app.uid} · 同 UID 应用共享授权。Shell 策略不受影响。")
        Action("允许 Root（内核默认策略）", primary = true, enabled = !busy) { save(app.profile.grant(true)) }
        Action("拒绝 Root，卸载模块挂载", enabled = !busy) { save(app.profile.deny(true)) }
        Action("撤销 Root，恢复默认非 Root 策略", enabled = !busy) { save(app.profile.grant(false)) }
        Text("自定义应用策略", style = MiuixTheme.textStyles.headline2)
        TextField(uid, { uid = it }, label = "目标 UID", modifier = Modifier.fillMaxWidth())
        TextField(gid, { gid = it }, label = "目标 GID", modifier = Modifier.fillMaxWidth())
        Hint("挂载命名空间：${listOf("继承", "全局", "独立")[namespace]}")
        Action("切换命名空间") { namespace = (namespace + 1) % 3 }
        val valid = uid.toIntOrNull()?.let { it >= 0 } == true && gid.toIntOrNull()?.let { it >= 0 } == true
        if (!valid) Text("UID / GID 必须为非负整数", color = MiuixTheme.colorScheme.error)
        Action("应用自定义策略", enabled = !busy && valid) { save(app.profile.custom(uid.toInt(), gid.toInt(), namespace)) }
        Hint("新建自定义策略采用标准 ksu SELinux 域与完整 capability；修改已有自定义策略时保留其组、capability、SELinux 域与 flags。")
        Text("完整策略与配置模板", style = MiuixTheme.textStyles.headline2)
        Hint("JSON 字段：uid、gid、namespace（0 继承 / 1 全局 / 2 独立）、groups（最多 32 个）、capabilities（Linux 位掩码）、context、flags（NO_NEW_PRIVS）。")
        TextField(templateText, { templateText = it }, label = "完整策略 JSON", modifier = Modifier.fillMaxWidth())
        policyError?.let { Text(it, color = MiuixTheme.colorScheme.error) }
        Action("应用完整策略", enabled = !busy) {
            try { save(app.profile.apply(PolicyTemplate.parse(templateText))) }
            catch (error: java.io.IOException) { policyError = error.message }
        }
        TextField(templateName, { templateName = it }, label = "模板名称", modifier = Modifier.fillMaxWidth())
        Action("保存为模板", enabled = !busy && templateName.isNotBlank()) { saveTemplate(templateName, templateText) }
        templates.forEach { (name, template) ->
            Action("载入模板：$name", enabled = !busy) { templateText = template.serialize(); templateName = name; policyError = null }
            Action("删除模板：$name", enabled = !busy) { deleteTemplate(name) }
        }
    }
}

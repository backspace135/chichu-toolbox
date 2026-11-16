package com.chichu.toolbox

import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chichu.toolbox.core.ksu.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

internal data class RootState(
    val loading: Boolean = false,
    val snapshot: RootSnapshot? = null,
    val applications: List<RootApplication>? = null,
    val modules: List<RootModule> = emptyList(),
    val busy: Boolean = false,
    val result: String? = null,
    val error: String? = null,
    val modulesLoaded: Boolean = false,
    val templates: Map<String, PolicyTemplate> = emptyMap(),
    val sepolicies: Map<String, String> = emptyMap(),
) {
    val policySummary: String get() {
        val current = snapshot ?: return "授权列表不可读取：尚未取得内核状态。"
        if (!current.canManage) return "授权列表不可读取：${current.issue}"
        val apps = applications ?: return "授权列表尚未成功读取，请验证管理通道；读取失败时查看上方诊断。"
        return "${apps.filter { it.profile.allowed }.map { it.uid }.distinct().size} 个 UID / ${apps.count { it.profile.allowed }} 个应用已允许"
    }
}

internal class RootViewModel(val repository: KsuRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(RootState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        if (mutableState.value.busy) return
        refreshJob?.cancel()
        repository.invalidate()
        mutableState.value = RootState(loading = true)
        refreshJob = viewModelScope.launch {
            try {
                mutableState.value = RootState(snapshot = repository.snapshot(), templates = repository.templates())
            } catch (error: IOException) { mutableState.value = RootState(error = error.message) }
              catch (error: SecurityException) { mutableState.value = RootState(error = "系统拒绝读取 KernelSU 信息：${error.message}") }
        }
    }

    fun verify(existing: Boolean = false) = operate {
        mutableState.value = mutableState.value.copy(applications = null, modules = emptyList(), modulesLoaded = false)
        if (existing) {
            val result = repository.verifyExistingRoot()
            mutableState.value = mutableState.value.copy(snapshot = repository.snapshot(), result = "已有 Root 通道已验证；原版管理器身份保持不变。\n${result.output}")
            mutableState.value = mutableState.value.copy(modules = repository.modules(), modulesLoaded = true)
        } else {
            repository.verifyRoot()
            mutableState.value = mutableState.value.copy(snapshot = repository.snapshot(), result = "ksud Root 通道已验证：UID 0")
            loadContents()
        }
    }

    fun revalidate() {
        val channel = mutableState.value.snapshot?.access
        if (channel == null) { refresh(); return }
        if (mutableState.value.busy) return
        operate {
            when (channel) {
                RootAccess.Manager -> repository.verifyRoot()
                RootAccess.ExistingV5, RootAccess.ExistingV6 -> repository.verifyExistingRoot()
            }
            mutableState.value = mutableState.value.copy(snapshot = repository.snapshot())
        }
    }

    private suspend fun loadContents() {
        val apps = if (checkNotNull(mutableState.value.snapshot).canManage) repository.applications() else null
        mutableState.value = mutableState.value.copy(applications = apps)
        val modules = repository.modules()
        mutableState.value = mutableState.value.copy(modules = modules, modulesLoaded = true)
    }

    fun setPolicy(app: RootApplication, policy: AppPolicy, rules: String? = null) = operate {
        repository.setPolicy(app, policy)
        if (rules != null) {
            repository.applySepolicy(app.packageName, rules)
            mutableState.value = mutableState.value.copy(sepolicies = mutableState.value.sepolicies + (app.packageName to rules.trim()))
        }
        mutableState.value = mutableState.value.copy(applications = repository.applications(), result = "策略已写入并通过内核读回验证。已运行的 Root 进程不会被降权，请重启目标应用后验证。")
    }

    fun moduleAction(module: RootModule, action: String) = execute(listOf("module", action, KsuProtocol.moduleId(module.id)), reload = true)
    fun execute(args: List<String>, reload: Boolean = false) = operate {
        val result = repository.command(args)
        mutableState.value = mutableState.value.copy(result = "退出码 ${result.code}\n${result.transcript}")
        result.requireSuccess()
        if (reload) loadContents()
    }
    fun install(uri: Uri) = operate {
        val result = repository.installModule(uri)
        mutableState.value = mutableState.value.copy(result = "模块安装退出码 ${result.code}\n${result.transcript}\n是否需要重启以模块及 ksud 输出为准。")
        result.requireSuccess()
        loadContents()
    }
    fun logs(kernel: Boolean = false) = operate {
        mutableState.value = mutableState.value.copy(result = if (kernel) repository.kernelLogs() else repository.logs())
    }
    fun migrate() = operate {
        val result = repository.migrateDebugManager()
        result.requireSuccess()
        mutableState.value = mutableState.value.copy(result = "迁移命令完成；请重新打开工具箱并验证管理器身份。\n${result.output}")
    }
    fun saveTemplate(name: String, text: String) = operate {
        repository.saveTemplate(name, PolicyTemplate.parse(text))
        mutableState.value = mutableState.value.copy(templates = repository.templates(), result = "模板已保存，可在其他应用策略中使用。")
    }
    fun deleteTemplate(name: String) = operate {
        repository.deleteTemplate(name)
        mutableState.value = mutableState.value.copy(templates = repository.templates(), result = "模板已删除。")
    }

    /** Per-app SELinux rules live in the ksud store; load them into the editor. */
    fun loadSepolicy(app: RootApplication) = operate {
        val rules = repository.sepolicyOf(app.packageName)
        mutableState.value = mutableState.value.copy(sepolicies = mutableState.value.sepolicies + (app.packageName to rules))
    }

    /** Import the original ksud template library; existing names and invalid entries are skipped. */
    fun importKsudTemplates() = operate {
        val ids = repository.ksudTemplates()
        val current = repository.templates()
        val imported = ArrayList<String>()
        val skipped = ArrayList<String>()
        for (id in ids) {
            if (current.containsKey(id)) { skipped.add(id); continue }
            try {
                repository.saveTemplate(id, PolicyTemplate.fromUpstream(repository.ksudTemplate(id)))
                imported.add(id)
            } catch (_: IOException) { skipped.add(id) }
        }
        mutableState.value = mutableState.value.copy(templates = repository.templates(),
            result = "ksud 模板库共 ${ids.size} 项：导入 ${imported.size} 项" +
                (if (skipped.isEmpty()) "。" else "；跳过 ${skipped.size} 项（重名或格式不受支持）。"))
    }
    private fun operate(block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        refreshJob?.cancel()
        mutableState.value = mutableState.value.copy(busy = true, error = null, result = null)
        viewModelScope.launch {
            try { block() }
            catch (error: IOException) {
                val message = error.message ?: error.javaClass.simpleName
                mutableState.value = mutableState.value.copy(error = message)
                try { repository.recordFailure(message) }
                catch (logError: IOException) {
                    mutableState.value = mutableState.value.copy(error = "$message\n故障日志保存失败：${logError.message}")
                }
            }
            catch (error: SecurityException) { mutableState.value = mutableState.value.copy(error = "系统拒绝操作：${error.message}") }
            catch (error: PackageManager.NameNotFoundException) { mutableState.value = mutableState.value.copy(error = "应用已卸载，请刷新：${error.message}") }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
}

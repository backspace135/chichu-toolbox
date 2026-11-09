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
    val applications: List<RootApplication> = emptyList(),
    val modules: List<RootModule> = emptyList(),
    val busy: Boolean = false,
    val result: String? = null,
    val error: String? = null,
    val loaded: Boolean = false,
    val templates: Map<String, PolicyTemplate> = emptyMap(),
)

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
        if (existing) {
            val result = repository.verifyExistingRoot()
            mutableState.value = mutableState.value.copy(snapshot = repository.snapshot(), result = "已有 Root 通道已验证；原版管理器身份保持不变。\n${result.output}")
            mutableState.value = mutableState.value.copy(modules = repository.modules(), loaded = true)
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
        val apps = if (checkNotNull(mutableState.value.snapshot).canManage) repository.applications() else emptyList()
        val modules = repository.modules()
        mutableState.value = mutableState.value.copy(applications = apps, modules = modules, loaded = true)
    }

    fun setPolicy(app: RootApplication, policy: AppPolicy) = operate {
        repository.setPolicy(app, policy)
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

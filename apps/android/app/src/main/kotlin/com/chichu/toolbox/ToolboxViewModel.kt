package com.chichu.toolbox

import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chichu.toolbox.core.common.ServiceState
import com.chichu.toolbox.core.common.ShellGrant
import com.chichu.toolbox.core.ghostlock.ConfigValidationException
import com.chichu.toolbox.core.shizuku.PermissionPrompt
import com.chichu.toolbox.core.shizuku.adb.AdbDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ToolboxViewModel(private val container: AppContainer) : ViewModel() {
    val service = container.service.state
    val activation = container.activation
    val discovery = AdbDiscovery(container.application)
    private val mutableApps = MutableStateFlow(ApplicationsState())
    val applications = mutableApps.asStateFlow()
    private val mutablePrompt = MutableStateFlow<PermissionPrompt?>(null)
    val prompt = mutablePrompt.asStateFlow()
    private val mutablePromptBusy = MutableStateFlow(false)
    val promptBusy = mutablePromptBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableAppearance = MutableStateFlow(Appearance(
        container.preferences.getString("mode", "system") ?: "system",
        container.preferences.getBoolean("dynamic", false),
    ))
    val appearance = mutableAppearance.asStateFlow()
    private val mutableDiagnostics = MutableStateFlow(DiagnosticsState())
    val diagnostics = mutableDiagnostics.asStateFlow()
    private val mutableConfigImport = MutableStateFlow(ConfigImportState())
    val configImport = mutableConfigImport.asStateFlow()
    private var diagnosticsJob: Job? = null
    private var refreshJob: Job? = null
    private val prompts = PermissionPrompts()
    val originalInstalled = try {
        container.application.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    init {
        viewModelScope.launch {
            service.collect { state ->
                if (state is ServiceState.Connected) refresh() else {
                    refreshJob?.cancel()
                    mutableApps.value = ApplicationsState()
                    mutablePrompt.value = null
                    prompts.clear()
                    mutablePromptBusy.value = false
                }
            }
        }
    }

    fun foreground() {
        viewModelScope.launch(Dispatchers.IO) { container.service.revalidate() }
        refreshDiagnostics()
    }

    fun refreshDiagnostics() {
        diagnosticsJob?.cancel()
        // Do not carry an old snapshot through a fresh foreground probe.
        mutableDiagnostics.value = DiagnosticsState(loading = true)
        diagnosticsJob = viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { container.diagnostics.read() }
                mutableDiagnostics.value = DiagnosticsState(snapshot = snapshot)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableDiagnostics.value = DiagnosticsState(error = "无法读取设备信息，请重新检测")
            }
        }
    }

    fun importKernelConfig(uri: Uri) {
        if (mutableConfigImport.value.loading) return
        // A failed replacement must not leave the previous file displayed as the current result.
        mutableConfigImport.value = ConfigImportState(loading = true)
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    val input = container.application.contentResolver.openInputStream(uri)
                        ?: error("无法打开配置文件")
                    input.use(container.configInspector::inspect)
                }
                mutableConfigImport.value = ConfigImportState(summary = summary)
            } catch (error: CancellationException) { throw error
            } catch (error: ConfigValidationException) {
                mutableConfigImport.value = ConfigImportState(error = error.message)
            } catch (_: Exception) {
                mutableConfigImport.value = ConfigImportState(error = "无法读取文件，请重新选择本地 .config 或 .config.gz")
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        if (service.value !is ServiceState.Connected) return
        refreshJob = viewModelScope.launch {
            mutableApps.value = mutableApps.value.copy(loading = true, error = null)
            try {
                val items = withContext(Dispatchers.IO) {
                    container.authorization.applications().map { app ->
                        val info = requireNotNull(app.packageInfo.applicationInfo)
                        ApplicationItem(info.loadLabel(container.application.packageManager).toString(), app.packageInfo.packageName, info.uid, app.grant)
                    }.sortedBy { it.label.lowercase() }
                }
                mutableApps.value = ApplicationsState(items)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                mutableApps.value = mutableApps.value.copy(loading = false, error = error.message ?: "无法加载应用，请重试")
            }
        }
    }

    fun setGrant(item: ApplicationItem, grant: ShellGrant) {
        if (mutableApps.value.changingUid != null) return
        mutableApps.value = mutableApps.value.copy(changingUid = item.uid)
        viewModelScope.launch {
            try {
                container.authorization.setGrant(item.uid, grant)
                refresh()
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableMessage.value = error.message ?: "授权操作失败，请重试"
            } finally { mutableApps.value = mutableApps.value.copy(changingUid = null) }
        }
    }

    fun receivePermissionToken(token: String) {
        if (token == prompts.current?.token || token.length !in 1..128) return
        // 先验证候选请求；无效的外部 Intent 不能关闭正在显示的真实请求。
        viewModelScope.launch {
            try {
                withTimeout(10_000) { service.first { it is ServiceState.Connected } }
                val verified = container.authorization.pending(token)
                if (verified == null) {
                    if (prompts.current == null) mutableMessage.value = "授权请求已失效，请在请求应用中重试"
                    return@launch
                }
                prompts.replace(verified)?.let { old ->
                    container.applicationScope.launch { runCatching { container.authorization.confirm(old, false) } }
                }
                mutablePrompt.value = prompts.current
                mutablePromptBusy.value = prompts.busy
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableMessage.value = error.message ?: "无法验证授权请求" }
        }
    }

    fun answerPermission(allowed: Boolean) {
        val request = mutablePrompt.value ?: return
        if (!prompts.begin(request)) return
        mutablePromptBusy.value = true
        viewModelScope.launch {
            try {
                container.authorization.confirm(request, allowed)
                refresh()
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                mutableMessage.value = error.message ?: "授权确认失败"
            } finally {
                prompts.finish(request)
                mutablePrompt.value = prompts.current
                mutablePromptBusy.value = prompts.busy
            }
        }
    }

    fun setAppearance(value: Appearance) {
        mutableAppearance.value = value
        container.preferences.edit().putString("mode", value.mode).putBoolean("dynamic", value.dynamic).apply()
    }
    fun clearMessage() { mutableMessage.value = null }

    override fun onCleared() {
        mutablePrompt.value?.let { request ->
            container.applicationScope.launch { runCatching { container.authorization.confirm(request, false) } }
        }
    }
}

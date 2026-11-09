package com.chichu.toolbox

import android.app.Application
import com.chichu.toolbox.core.ghostlock.DeviceDiagnostics
import com.chichu.toolbox.core.ghostlock.KernelConfigInspector
import com.chichu.toolbox.core.ksu.KsuRepository
import com.chichu.toolbox.core.shizuku.AuthorizationRepository
import com.chichu.toolbox.core.shizuku.ShizukuRepository
import com.chichu.toolbox.core.shizuku.activation.ActivationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ToolboxApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
    }
}

class AppContainer(val application: Application) {
    val service = ShizukuRepository.instance
    val authorization = AuthorizationRepository(service)
    val diagnostics = DeviceDiagnostics()
    val configInspector = KernelConfigInspector()
    val root = KsuRepository(application)
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // One operation gate and status shared by the foreground UI and notification pairing service.
    val activation = ActivationController(application, service, applicationScope)
    val preferences = application.getSharedPreferences("appearance", Application.MODE_PRIVATE)
}

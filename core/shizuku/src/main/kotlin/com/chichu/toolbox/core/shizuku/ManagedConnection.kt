package com.chichu.toolbox.core.shizuku

import com.chichu.toolbox.core.common.ServiceIdentity
import com.chichu.toolbox.core.common.ServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ServiceEndpoint {
    fun identity(): ServiceIdentity
    fun isAlive(): Boolean
    fun watchDeath(onDeath: () -> Unit)
    fun unwatchDeath()
}

class ManagedConnection {
    private val mutableState = MutableStateFlow<ServiceState>(ServiceState.Offline)
    val state = mutableState.asStateFlow()
    private var endpoint: ServiceEndpoint? = null

    @Synchronized
    fun connect(candidate: ServiceEndpoint) {
        endpoint?.unwatchDeath()
        endpoint = candidate
        mutableState.value = ServiceState.Activating
        try {
            candidate.watchDeath { disconnected(candidate) }
            val identity = candidate.identity()
            check(identity.uid == 2000) { "内置权限服务必须以 Shell 身份运行" }
            if (endpoint === candidate && candidate.isAlive()) {
                mutableState.value = ServiceState.Connected(identity)
            } else {
                disconnected(candidate)
            }
        } catch (error: Exception) {
            if (endpoint === candidate) {
                candidate.unwatchDeath()
                endpoint = null
                mutableState.value = ServiceState.Failed(error.message ?: "连接内置服务失败")
            }
        }
    }

    @Synchronized
    private fun disconnected(candidate: ServiceEndpoint) {
        if (endpoint !== candidate) return
        endpoint = null
        mutableState.value = ServiceState.Offline
    }

    @Synchronized
    fun requireEndpoint(): ServiceEndpoint {
        val current = endpoint
        if (current != null && !current.isAlive()) disconnected(current)
        check(current != null && endpoint === current && state.value is ServiceState.Connected) {
            "内置权限服务未连接"
        }
        return current
    }
}

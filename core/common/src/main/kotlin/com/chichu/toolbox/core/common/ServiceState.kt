package com.chichu.toolbox.core.common

data class ServiceIdentity(val uid: Int, val version: Int, val patchVersion: Int)

sealed interface ServiceState {
    data object Offline : ServiceState
    data object Activating : ServiceState
    data class Connected(val identity: ServiceIdentity) : ServiceState
    data class Failed(val message: String) : ServiceState
}

class ServiceSession {
    var state: ServiceState = ServiceState.Offline
        private set
    private var generation = 0L

    @Synchronized
    fun beginActivation(): Long {
        state = ServiceState.Activating
        return ++generation
    }

    @Synchronized
    fun connected(attempt: Long, identity: ServiceIdentity) {
        if (attempt != generation) return
        state = if (identity.uid == 2000) ServiceState.Connected(identity)
        else ServiceState.Failed("内置权限服务必须以 Shell 身份运行")
    }

    @Synchronized
    fun failed(attempt: Long, message: String) {
        if (attempt == generation) state = ServiceState.Failed(message)
    }

    @Synchronized
    fun disconnected() {
        generation++
        state = ServiceState.Offline
    }
}

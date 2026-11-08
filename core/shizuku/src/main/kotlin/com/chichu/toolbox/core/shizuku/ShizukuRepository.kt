package com.chichu.toolbox.core.shizuku

import android.os.IBinder

class ShizukuRepository internal constructor(private val connection: ManagedConnection) {
    val state = connection.state
    val shell = ShellChannel { command ->
        RemoteCommandProcess(endpoint().service.newProcess(command.toTypedArray(), null, null))
    }

    internal fun receive(binder: IBinder) = connection.connect(ManagerEndpoint(binder))
    internal fun endpoint() = connection.requireEndpoint() as ManagerEndpoint

    fun revalidate() {
        runCatching { connection.requireEndpoint() }
    }

    companion object {
        val instance = ShizukuRepository(ManagedConnection())
    }
}

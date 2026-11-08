package com.chichu.toolbox.core.shizuku

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess

internal class RemoteCommandProcess(private val process: IRemoteProcess) : CommandProcess {
    override val stdout = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
    override val stderr = ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)

    init {
        // 内部命令没有交互式输入，立即关闭 stdin，避免远端命令等待输入。
        process.outputStream.close()
    }

    override fun alive() = process.alive()
    override fun exitValue() = process.exitValue()
    override fun destroy() = process.destroy()
}

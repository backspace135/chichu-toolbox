package com.chichu.toolbox.core.shizuku

import android.os.IBinder
import android.os.Parcel
import com.chichu.toolbox.core.common.ServiceIdentity
import moe.shizuku.server.IShizukuService
import rikka.shizuku.ShizukuApiConstants
import rikka.shizuku.server.ServerConstants

internal class ManagerEndpoint(val binder: IBinder) : ServiceEndpoint {
    val service: IShizukuService = IShizukuService.Stub.asInterface(binder)
    private var deathRecipient: IBinder.DeathRecipient? = null

    override fun identity(): ServiceIdentity = transact(ServerConstants.BINDER_TRANSACTION_identity) { _, reply ->
        check(reply.readString() == ServerConstants.MANAGER_APPLICATION_ID) { "连接的不是工具箱内置服务" }
        ServiceIdentity(reply.readInt(), reply.readInt(), reply.readInt())
    }

    override fun isAlive() = binder.pingBinder()

    override fun watchDeath(onDeath: () -> Unit) {
        val recipient = IBinder.DeathRecipient { onDeath() }
        deathRecipient = recipient
        binder.linkToDeath(recipient, 0)
    }

    override fun unwatchDeath() {
        deathRecipient?.let { binder.unlinkToDeath(it, 0) }
        deathRecipient = null
    }

    fun <T> transact(code: Int, write: (Parcel) -> Unit = {}, read: (Parcel, Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ShizukuApiConstants.BINDER_DESCRIPTOR)
            write(data)
            check(binder.transact(code, data, reply, 0)) { "内置服务不支持此管理操作" }
            reply.readException()
            return read(data, reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}

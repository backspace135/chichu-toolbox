package com.chichu.toolbox.core.shizuku

import android.content.pm.PackageInfo
import android.os.Bundle
import com.chichu.toolbox.core.common.ShellGrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.parcelablelist.ParcelableListSlice
import rikka.shizuku.ShizukuApiConstants
import rikka.shizuku.server.ServerConstants

data class AuthorizedApplication(val packageInfo: PackageInfo, val grant: ShellGrant)
data class PermissionPrompt(val token: String, val uid: Int, val pid: Int, val requestCode: Int, val packageName: String)

class AuthorizationRepository(private val repository: ShizukuRepository) {
    suspend fun applications(): List<AuthorizedApplication> = withContext(Dispatchers.IO) {
        val endpoint = repository.endpoint()
        val packages = endpoint.transact(ServerConstants.BINDER_TRANSACTION_getApplications, { it.writeInt(-1) }) { _, reply ->
            @Suppress("UNCHECKED_CAST")
            (ParcelableListSlice.CREATOR.createFromParcel(reply) as ParcelableListSlice<PackageInfo>).list
        }
        packages.map { info ->
            val flags = endpoint.service.getFlagsForUid(requireNotNull(info.applicationInfo).uid, MASK_PERMISSION)
            AuthorizedApplication(info, when {
                flags and FLAG_ALLOWED != 0 -> ShellGrant.Allowed
                flags and FLAG_DENIED != 0 -> ShellGrant.Denied
                else -> ShellGrant.Ask
            })
        }
    }

    suspend fun setGrant(uid: Int, grant: ShellGrant) = withContext(Dispatchers.IO) {
        repository.endpoint().service.updateFlagsForUid(uid, MASK_PERMISSION, when (grant) {
            ShellGrant.Allowed -> FLAG_ALLOWED
            ShellGrant.Denied -> FLAG_DENIED
            ShellGrant.Ask -> 0
        })
    }

    suspend fun pending(token: String): PermissionPrompt? = withContext(Dispatchers.IO) {
        repository.endpoint().transact(ServerConstants.BINDER_TRANSACTION_pendingPermission, { it.writeString(token) }) { _, reply ->
            if (reply.readInt() == 0) null else PermissionPrompt(token, reply.readInt(), reply.readInt(), reply.readInt(), requireNotNull(reply.readString()))
        }
    }

    suspend fun confirm(prompt: PermissionPrompt, allowed: Boolean, onetime: Boolean = false) = withContext(Dispatchers.IO) {
        repository.endpoint().transact(ServerConstants.BINDER_TRANSACTION_confirmPermission, { data ->
            data.writeInt(prompt.uid)
            data.writeInt(prompt.pid)
            data.writeInt(prompt.requestCode)
            data.writeBundle(Bundle().apply {
                putString(ServerConstants.EXTRA_PERMISSION_TOKEN, prompt.token)
                putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
                putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
            })
        }) { _, _ -> Unit }
    }

    private companion object {
        const val FLAG_ALLOWED = 1 shl 1
        const val FLAG_DENIED = 1 shl 2
        const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED
    }
}

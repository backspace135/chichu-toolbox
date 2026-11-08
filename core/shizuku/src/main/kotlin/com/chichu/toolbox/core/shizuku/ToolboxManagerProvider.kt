package com.chichu.toolbox.core.shizuku

import android.os.Binder
import android.os.Bundle
import moe.shizuku.api.BinderContainer
import rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TOKEN
import rikka.shizuku.ShizukuProvider

// 管理器必须实现固定版本的官方内部 Binder 协议，不作为第三方公共 API 使用。
@android.annotation.SuppressLint("RestrictedApi")
class ToolboxManagerProvider : ShizukuProvider() {
    override fun onCreate(): Boolean {
        disableAutomaticSuiInitialization()
        return super.onCreate()
    }

    @Suppress("DEPRECATION")
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (Binder.getCallingUid() != 2000) throw SecurityException("仅接受 Shell 服务中转")
        extras ?: return null
        extras.classLoader = BinderContainer::class.java.classLoader
        val binder = extras.getParcelable<BinderContainer>(EXTRA_BINDER)?.binder ?: return null
        // 清除 Shell 调用身份，再以管理器自身 UID 完成远端身份鉴权。
        val identity = Binder.clearCallingIdentity()
        return try {
            val repository = ShizukuRepository.instance
            when (method) {
                METHOD_SEND_BINDER -> {
                    repository.receive(binder)
                    if (repository.state.value !is com.chichu.toolbox.core.common.ServiceState.Connected) null
                    else Bundle()
                }
                "sendUserService" -> {
                    val token = extras.getString(USER_SERVICE_ARG_TOKEN) ?: return null
                    val endpoint = repository.endpoint()
                    endpoint.service.attachUserService(binder, Bundle().apply { putString(USER_SERVICE_ARG_TOKEN, token) })
                    Bundle().apply { putParcelable(EXTRA_BINDER, BinderContainer(endpoint.binder)) }
                }
                else -> null
            }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private companion object {
        const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
    }
}

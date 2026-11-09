package com.chichu.toolbox

import com.chichu.toolbox.core.shizuku.PermissionPrompt

internal class PermissionPrompts {
    var current: PermissionPrompt? = null
        private set
    private val confirming = mutableSetOf<String>()
    val busy get() = current?.token in confirming

    fun replace(verified: PermissionPrompt?): PermissionPrompt? {
        if (verified == null || current?.token == verified.token) return null
        val reject = current?.takeUnless { it.token in confirming }
        current = verified
        return reject
    }

    fun begin(request: PermissionPrompt): Boolean = current?.token == request.token && confirming.add(request.token)

    fun finish(request: PermissionPrompt) {
        confirming.remove(request.token)
        if (current?.token == request.token) current = null
    }

    fun clear() { current = null }
}

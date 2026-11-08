package com.chichu.toolbox.core.common

enum class ShellGrant { Ask, Allowed, Denied }

object ShellAuthorization {
    fun canExecute(grant: ShellGrant?): Boolean = grant == ShellGrant.Allowed
    fun revoke(@Suppress("UNUSED_PARAMETER") grant: ShellGrant): ShellGrant = ShellGrant.Ask
}

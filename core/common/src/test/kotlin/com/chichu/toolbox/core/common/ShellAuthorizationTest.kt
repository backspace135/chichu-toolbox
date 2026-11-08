package com.chichu.toolbox.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellAuthorizationTest {
    @Test
    fun missingGrantCannotExecute() {
        assertFalse(ShellAuthorization.canExecute(null))
        assertFalse(ShellAuthorization.canExecute(ShellGrant.Ask))
    }

    @Test
    fun onlyExplicitApprovalCanExecute() {
        assertTrue(ShellAuthorization.canExecute(ShellGrant.Allowed))
        assertFalse(ShellAuthorization.canExecute(ShellGrant.Denied))
    }

    @Test
    fun revokedApprovalCannotExecute() {
        val revoked = ShellAuthorization.revoke(ShellGrant.Allowed)

        assertFalse(ShellAuthorization.canExecute(revoked))
    }
}

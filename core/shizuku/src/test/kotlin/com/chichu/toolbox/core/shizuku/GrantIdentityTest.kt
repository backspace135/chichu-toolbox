package com.chichu.toolbox.core.shizuku

import org.junit.Assert.*
import org.junit.Test
import rikka.shizuku.server.GrantIdentity

class GrantIdentityTest {
    @Test fun recycledUidDoesNotMatchAnotherPackage() {
        assertFalse(GrantIdentity.matches(listOf("old.app"), listOf("new.app")))
    }
    @Test fun missingPackagesFailClosed() {
        assertFalse(GrantIdentity.matches(null, listOf("new.app")))
        assertFalse(GrantIdentity.matches(listOf("old.app"), emptyList()))
    }
    @Test fun sharedUidKeepsGrantOnlyWhileKnownIdentityStillExists() {
        assertTrue(GrantIdentity.matches(listOf("known.app"), listOf("known.app", "shared.app")))
    }
    @Test fun revokeIncludesInstalledPackagesWithoutLiveClients() {
        assertEquals(setOf("daemon.app"), GrantIdentity.revocationTargets(listOf("daemon.app"), emptyList()))
    }
}

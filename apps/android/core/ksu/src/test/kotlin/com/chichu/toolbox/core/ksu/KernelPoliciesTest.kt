package com.chichu.toolbox.core.ksu

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class KernelPoliciesTest {
    private val initial = AppPolicy.absent("com.example.test", 10123)

    @Test fun grantDenyAndRevokeUseActualKernelState() {
        var data = ByteArray(0)
        val store = KernelPolicies({ _, _ -> data }, { data = it })
        val allowed = store.set("com.example.test", 10123, initial.grant(true))
        assertTrue(allowed.allowed)
        val denied = store.set("com.example.test", 10123, allowed.deny(true))
        assertFalse(denied.allowed)
        assertTrue(denied.umount)
        val revoked = store.set("com.example.test", 10123, denied.grant(false))
        assertFalse(revoked.allowed)
        assertTrue(revoked.useDefault)
    }

    @Test fun failedIoctlDoesNotReturnAnAllowedProfile() {
        val store = KernelPolicies({ _, _ -> throw IOException("EPERM") }, { throw IOException("EPERM") })
        assertThrows(IOException::class.java) { store.get("com.example.test", 10123) }
        assertThrows(IOException::class.java) { store.set("com.example.test", 10123, initial.grant(true)) }
    }

    @Test fun unchangedOrMissingReadbackIsReportedAsFailure() {
        val stale = KernelPolicies({ _, _ -> initial.encode() }, {})
        assertThrows(IOException::class.java) { stale.set("com.example.test", 10123, initial.grant(true)) }
        val absent = KernelPolicies({ _, _ -> ByteArray(0) }, {})
        assertThrows(IOException::class.java) { absent.set("com.example.test", 10123, initial.grant(false)) }
    }

    @Test fun customPolicyReadbackMustMatchRequestedCredentials() {
        val policy = initial.custom(0, 0, 2)
        val stale = KernelPolicies({ _, _ -> initial.custom(2000, 0, 2).encode() }, {})
        assertThrows(IOException::class.java) { stale.set("com.example.test", 10123, policy) }
    }
}

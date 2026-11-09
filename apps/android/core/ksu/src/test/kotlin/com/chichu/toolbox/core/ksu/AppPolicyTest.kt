package com.chichu.toolbox.core.ksu

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AppPolicyTest {
    @Test fun absentPolicyDoesNotAllowRoot() {
        val value = AppPolicy.absent("com.example.test", 10123)
        assertFalse(value.configured)
        assertFalse(value.allowed)
        assertTrue(value.useDefault)
        assertEquals(10123, ByteBuffer.wrap(value.encode()).order(ByteOrder.LITTLE_ENDIAN).getInt(260))
    }
    @Test fun rootAndNonrootUnionIsClearedWhenChangingGrant() {
        val custom = AppPolicy.absent("com.example.test", 10123).custom(0, 0, 2)
        assertTrue(custom.allowed)
        assertEquals(2, custom.namespace)
        val denied = custom.deny(true)
        assertFalse(denied.allowed)
        assertFalse(denied.useDefault)
        assertTrue(denied.umount)
        val allowed = denied.grant(true)
        assertTrue(allowed.allowed)
        assertTrue(allowed.useDefault)
        assertEquals(0, allowed.uid)
        assertEquals(0, allowed.namespace)
    }
    @Test fun editingExistingCustomPolicyPreservesOtherCredentials() {
        val original = AppPolicy.absent("com.example.test", 10123).custom(0, 0, 1).encode()
        ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN).putLong(680, 8).putLong(776, 0)
        val updated = AppPolicy.decode(original).custom(2000, 2000, 2)
        val data = ByteBuffer.wrap(updated.encode()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(8L, data.getLong(680))
        assertEquals(0L, data.getLong(776))
        assertEquals(2000, updated.uid)
        assertEquals(2, updated.namespace)
    }
    @Test fun malformedKernelProfilesAreErrors() {
        assertThrows(IOException::class.java) { AppPolicy.decode(ByteArray(783)) }
        assertThrows(IOException::class.java) { AppPolicy.decode(ByteArray(784)) }
    }
}

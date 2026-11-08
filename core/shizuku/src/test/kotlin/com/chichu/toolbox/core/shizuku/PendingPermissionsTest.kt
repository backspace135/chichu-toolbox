package com.chichu.toolbox.core.shizuku

import org.junit.Assert.*
import org.junit.Test
import rikka.shizuku.server.PendingPermissions

class PendingPermissionsTest {
    @Test fun unknownTokenCannotApproveRequest() {
        assertNull(PendingPermissions().consume("forged", 10001, 42, 7, 1000))
    }

    @Test fun requestCanOnlyBeConsumedOnce() {
        val store = PendingPermissions()
        val token = store.issue(10001, 42, 7, "test.client", 1000)
        assertNotNull(store.consume(token, 10001, 42, 7, 1001))
        assertNull(store.consume(token, 10001, 42, 7, 1002))
    }

    @Test fun mismatchedIdentityDoesNotConsumeValidRequest() {
        val store = PendingPermissions()
        val token = store.issue(10001, 42, 7, "test.client", 1000)
        assertNull(store.consume(token, 10002, 42, 7, 1001))
        assertNotNull(store.consume(token, 10001, 42, 7, 1002))
    }

    @Test fun expiredPromptCannotApprove() {
        val store = PendingPermissions()
        val token = store.issue(10001, 42, 7, "test.client", 1000)
        assertNull(store.consume(token, 10001, 42, 7, 301001))
    }

    @Test fun managerReadsServerIdentityWithoutConsumingRequest() {
        val store = PendingPermissions()
        val token = store.issue(10001, 42, 7, "test.client", 1000)
        val request = store.peek(token, 1001)!!
        assertEquals("test.client", request.packageName)
        assertEquals(10001, request.uid)
        assertEquals(42, request.pid)
        assertEquals(7, request.requestCode)
        assertNotNull(store.consume(token, 10001, 42, 7, 1002))
    }

    @Test fun expiredAndUnknownRequestsCannotBeDisplayed() {
        val store = PendingPermissions()
        val token = store.issue(10001, 42, 7, "test.client", 1000)
        assertNull(store.peek("forged", 1001))
        assertNull(store.peek(token, 301000))
    }

    @Test fun mismatchedPidOrRequestCodeCannotApprove() {
        val store = PendingPermissions()
        val token = store.issue(10001, 42, 7, "test.client", 1000)
        assertNull(store.consume(token, 10001, 43, 7, 1001))
        assertNull(store.consume(token, 10001, 42, 8, 1001))
        assertNotNull(store.consume(token, 10001, 42, 7, 1002))
    }

    @Test fun newPromptInvalidatesPreviousTokenForSameRequest() {
        val store = PendingPermissions()
        val previous = store.issue(10001, 42, 7, "test.client", 1000)
        val current = store.issue(10001, 42, 7, "test.client", 1001)
        assertNotEquals(previous, current)
        assertNull(store.consume(previous, 10001, 42, 7, 1002))
        assertNotNull(store.consume(current, 10001, 42, 7, 1002))
    }
}

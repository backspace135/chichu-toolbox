package com.chichu.toolbox.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServiceStateTest {
    @Test
    fun binderDeathClearsConnectionMetadata() {
        val session = ServiceSession()
        val attempt = session.beginActivation()
        session.connected(attempt, ServiceIdentity(uid = 2000, version = 13, patchVersion = 6))

        session.disconnected()

        assertEquals(ServiceState.Offline, session.state)
        assertFalse(session.state is ServiceState.Connected)
    }

    @Test
    fun obsoleteActivationCannotReviveDeadConnection() {
        val session = ServiceSession()
        val attempt = session.beginActivation()
        session.disconnected()

        session.connected(attempt, ServiceIdentity(2000, 13, 6))

        assertEquals(ServiceState.Offline, session.state)
    }

    @Test
    fun oldFailureCannotOverwriteNewConnection() {
        val session = ServiceSession()
        val oldAttempt = session.beginActivation()
        val newAttempt = session.beginActivation()
        val identity = ServiceIdentity(2000, 13, 6)
        session.connected(newAttempt, identity)

        session.failed(oldAttempt, "旧连接已超时")

        assertEquals(ServiceState.Connected(identity), session.state)
    }

    @Test
    fun rootIdentityIsNotAcceptedAsShellService() {
        val session = ServiceSession()
        val attempt = session.beginActivation()

        session.connected(attempt, ServiceIdentity(0, 13, 6))

        assertFalse(session.state is ServiceState.Connected)
    }
}

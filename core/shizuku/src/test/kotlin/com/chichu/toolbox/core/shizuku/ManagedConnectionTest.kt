package com.chichu.toolbox.core.shizuku

import com.chichu.toolbox.core.common.ServiceIdentity
import com.chichu.toolbox.core.common.ServiceState
import org.junit.Assert.*
import org.junit.Test

class ManagedConnectionTest {
    private class Endpoint : ServiceEndpoint {
        var alive = true
        var death: () -> Unit = {}
        var identityFailure = false
        var uid = 2000
        override fun identity(): ServiceIdentity {
            check(!identityFailure) { "远端校验失败" }
            return ServiceIdentity(uid, 13, 6)
        }
        override fun isAlive() = alive
        override fun watchDeath(onDeath: () -> Unit) { death = onDeath }
        override fun unwatchDeath() = Unit
    }

    @Test fun deathClearsConnectionAndRejectsOperations() {
        val connection = ManagedConnection()
        val endpoint = Endpoint()
        connection.connect(endpoint)
        assertTrue(connection.state.value is ServiceState.Connected)
        endpoint.death()
        assertEquals(ServiceState.Offline, connection.state.value)
        assertThrows(IllegalStateException::class.java) { connection.requireEndpoint() }
    }

    @Test fun failedIdentityNeverPublishesConnected() {
        val connection = ManagedConnection()
        connection.connect(Endpoint().apply { identityFailure = true })
        assertTrue(connection.state.value is ServiceState.Failed)
        assertThrows(IllegalStateException::class.java) { connection.requireEndpoint() }
    }

    @Test fun rootEndpointCannotBecomeShellConnection() {
        val connection = ManagedConnection()
        connection.connect(Endpoint().apply { uid = 0 })
        assertTrue(connection.state.value is ServiceState.Failed)
    }

    @Test fun oldBinderDeathCannotClearReplacement() {
        val connection = ManagedConnection()
        val old = Endpoint()
        val current = Endpoint()
        connection.connect(old)
        connection.connect(current)
        old.death()
        assertSame(current, connection.requireEndpoint())
    }

    @Test fun deathDuringHandshakeCannotReviveConnection() {
        val connection = ManagedConnection()
        val endpoint = object : ServiceEndpoint {
            override fun identity() = ServiceIdentity(2000, 13, 6)
            override fun isAlive() = true
            override fun watchDeath(onDeath: () -> Unit) = onDeath()
            override fun unwatchDeath() = Unit
        }
        connection.connect(endpoint)
        assertEquals(ServiceState.Offline, connection.state.value)
    }
}

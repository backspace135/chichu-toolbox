package com.chichu.toolbox.core.shizuku

import com.chichu.toolbox.core.shizuku.activation.ActivationGate
import org.junit.Assert.*
import org.junit.Test

class ActivationGateTest {
    @Test fun oldCleanupCannotInterruptNewConnection() {
        val gate = ActivationGate()
        val old = gate.begin()!!
        var oldClosed = false
        gate.register(old) { oldClosed = true }
        gate.cancel()
        assertTrue(oldClosed)
        val current = gate.begin()!!
        var currentClosed = false
        gate.register(current) { currentClosed = true }
        gate.finish(old)
        gate.interrupt(old)
        assertFalse(currentClosed)
        gate.interrupt(current)
        assertTrue(currentClosed)
    }

    @Test fun clientRegisteredAfterCancellationIsImmediatelyInterrupted() {
        val gate = ActivationGate()
        val old = gate.begin()!!
        gate.cancel()
        var closed = false
        gate.register(old) { closed = true }
        assertTrue(closed)
    }
    @Test fun duplicateOperationIsRejectedUntilFirstFinishes() {
        val gate = ActivationGate()
        val first = gate.begin()!!
        assertNull(gate.begin())
        gate.finish(first)
        assertNotNull(gate.begin())
    }

    @Test fun canceledOperationCannotPublishSuccessOrClearNewOperation() {
        val gate = ActivationGate()
        val old = gate.begin()!!
        gate.cancel()
        val current = gate.begin()!!
        assertFalse(gate.isCurrent(old))
        gate.finish(old)
        assertTrue(gate.isCurrent(current))
    }
}

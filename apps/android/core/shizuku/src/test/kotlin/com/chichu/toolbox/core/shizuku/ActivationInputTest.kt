package com.chichu.toolbox.core.shizuku

import com.chichu.toolbox.core.shizuku.activation.ActivationInput
import org.junit.Assert.*
import org.junit.Test

class ActivationInputTest {
    @Test fun onlyValidTcpPortsAreAccepted() {
        assertEquals(1, ActivationInput.port("1"))
        assertEquals(65535, ActivationInput.port("65535"))
        listOf("0", "65536", "-1", "abc", "", " 1234").forEach {
            assertThrows(IllegalArgumentException::class.java) { ActivationInput.port(it) }
        }
    }

    @Test fun pairingCodeIsExactlySixAsciiDigits() {
        assertEquals("012345", ActivationInput.pairingCode("012345"))
        listOf("12345", "1234567", "123 56", "１２３４５６").forEach {
            assertThrows(IllegalArgumentException::class.java) { ActivationInput.pairingCode(it) }
        }
    }
}

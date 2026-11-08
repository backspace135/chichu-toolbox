package com.chichu.toolbox.core.shizuku

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream

class ShellChannelTest {
    private fun command(vararg args: String) = listOf(
        java.io.File(System.getProperty("java.home"), "bin/java").absolutePath,
        "-cp", java.io.File(requireNotNull(ShellFixture::class.java.protectionDomain).codeSource.location.toURI()).path,
        ShellFixture::class.java.name,
    ) + args
    private class LocalProcess(private val process: Process) : CommandProcess {
        override val stdout: InputStream get() = process.inputStream
        override val stderr: InputStream get() = process.errorStream
        override fun alive() = process.isAlive
        override fun exitValue() = process.exitValue()
        override fun destroy() { process.destroyForcibly() }
    }

    @Test fun capturesExitCodeAndBothOutputStreams() = runBlocking {
        val channel = ShellChannel { command -> LocalProcess(ProcessBuilder(command).start()) }
        val result = channel.execute(command())
        assertEquals(7, result.exitCode)
        assertTrue(result.stdout.contains("output"))
        assertTrue(result.stderr.contains("error"))
    }

    @Test fun outputIsBoundedWithoutBlockingPipeDrain() = runBlocking {
        val channel = ShellChannel { command -> LocalProcess(ProcessBuilder(command).start()) }
        val result = channel.execute(command("large"), maxOutputBytes = 100)
        assertTrue(result.truncated)
        assertTrue(result.stdout.toByteArray().size <= 100)
    }

    @Test fun timeoutDestroysProcessInsteadOfReportingSuccess() = runBlocking {
        var destroyed = false
        val process = object : CommandProcess {
            override val stdout = InputStream.nullInputStream()
            override val stderr = InputStream.nullInputStream()
            override fun alive() = true
            override fun exitValue() = 0
            override fun destroy() { destroyed = true }
        }
        try {
            ShellChannel { process }.execute(listOf("id"), timeoutMs = 30)
            fail("超时操作不应成功")
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            assertTrue(destroyed)
        }
    }
}

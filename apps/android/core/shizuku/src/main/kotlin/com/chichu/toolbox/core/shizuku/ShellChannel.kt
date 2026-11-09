package com.chichu.toolbox.core.shizuku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface CommandProcess {
    val stdout: InputStream
    val stderr: InputStream
    fun alive(): Boolean
    fun exitValue(): Int
    fun destroy()
}

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String, val truncated: Boolean)

class ShellChannel(private val start: (List<String>) -> CommandProcess) {
    suspend fun execute(command: List<String>, timeoutMs: Long = 15_000, maxOutputBytes: Int = 262_144): ShellResult {
        require(command.isNotEmpty() && command.none { '\u0000' in it })
        require(timeoutMs > 0 && maxOutputBytes > 0)
        return withContext(Dispatchers.IO) {
            coroutineScope {
                val process = start(command)
                val remaining = AtomicInteger(maxOutputBytes)
                val truncated = AtomicBoolean(false)
                val stdout = process.stdout
                val stderr = process.stderr
                try {
                    val output = async(Dispatchers.IO) { capture(stdout, remaining, truncated) }
                    val error = async(Dispatchers.IO) { capture(stderr, remaining, truncated) }
                    withTimeout(timeoutMs) {
                        while (process.alive()) delay(20)
                        ShellResult(process.exitValue(), output.await(), error.await(), truncated.get())
                    }
                } finally {
                    runCatching { process.destroy() }
                    runCatching { stdout.close() }
                    runCatching { stderr.close() }
                }
            }
        }
    }

    private fun capture(stream: InputStream, remaining: AtomicInteger, truncated: AtomicBoolean): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            val kept = minOf(count, remaining.getAndUpdate { (it - count).coerceAtLeast(0) })
            if (kept > 0) output.write(buffer, 0, kept)
            if (kept != count) truncated.set(true)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}

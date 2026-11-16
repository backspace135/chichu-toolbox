package com.chichu.toolbox.core.ksu

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

/**
 * One long-lived root shell for WebUI synchronous calls. The prompt bridge blocks the
 * calling thread, so every command must be a single small round trip; each command runs
 * in its own subshell (cwd / env isolation) with stderr discarded, and the exit code
 * rides on a per-command nonce marker the command output cannot forge.
 *
 * Single-threaded by contract: WebUI prompts arrive serialized from the UI thread, and
 * [exec] is [Synchronized] so the stdin/stdout pairing cannot interleave.
 */
class RootShellSession private constructor(private val process: Process) {
    private val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
    private val writer = process.outputStream.bufferedWriter()

    class Result(val exitCode: Int, val stdout: String)

    @Synchronized
    fun exec(command: String): Result {
        val nonce = UUID.randomUUID().toString().replace("-", "")
        writer.write("( $command ) 2>/dev/null\n")
        writer.write("echo __CHICHU_${nonce}_\$?\n")
        writer.flush()
        val lines = ArrayList<String>()
        while (true) {
            val line = reader.readLine() ?: throw IOException("Root 会话已结束")
            val marker = "__CHICHU_${nonce}_"
            if (line.startsWith(marker)) {
                val code = line.substring(marker.length).toIntOrNull()
                    ?: throw IOException("Root 会话返回码异常：$line")
                return Result(code, lines.joinToString("\n"))
            }
            lines.add(line)
        }
    }

    fun close() {
        try {
            writer.write("exit\n")
            writer.flush()
        } catch (_: IOException) {
            // The shell may already be gone; destroying the process is the guarantee.
        }
        process.destroy()
    }

    companion object {
        fun start(command: List<String>): RootShellSession {
            return RootShellSession(ProcessBuilder(command).start())
        }
    }
}

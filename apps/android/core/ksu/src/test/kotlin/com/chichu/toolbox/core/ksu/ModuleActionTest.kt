package com.chichu.toolbox.core.ksu

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ModuleActionTest {
    private fun runAction(script: String?): Pair<Int, String> {
        val directory = Files.createTempDirectory("module-action-").toFile()
        try {
            if (script != null) directory.resolve("action.sh").writeText(script)
            val command = KsuProtocol.moduleActionScript("test.module", KernelStatus(0, 32680, 5, 5), "3.3.1")
                .replace("/data/adb/modules/test.module", directory.absolutePath)
                .replace("/data/adb/ksu/bin/busybox sh", "/bin/sh")
            val process = ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            return process.waitFor() to output
        } finally { directory.deleteRecursively() }
    }

    @Test fun missingActionIsNotSuccess() {
        val (code, output) = runAction(null)
        assertNotEquals(output, 0, code)
    }

    @Test fun propagatesActionFailure() {
        val (code, output) = runAction("echo action-failed >&2\nexit 37\n")
        assertEquals(37, code)
        assertTrue(output.contains("action-failed"))
    }

    @Test fun successfulActionReceivesModuleEnvironment() {
        val (code, output) = runAction("printf '%s' \"\$KSU|\$KSU_MODULE|\$KSU_UAPI_VER|\$KSU_LATE_LOAD|\$KSU_VER\"\n")
        assertEquals(0, code)
        assertEquals("true|test.module|5|1|3.3.1", output)
    }
}

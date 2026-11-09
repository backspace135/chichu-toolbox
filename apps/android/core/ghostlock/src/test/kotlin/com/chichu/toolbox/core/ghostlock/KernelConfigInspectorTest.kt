package com.chichu.toolbox.core.ghostlock

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelConfigInspectorTest {
    private val inspector = KernelConfigInspector()

    @Test
    fun countsTypedValuesAndDisabledOptions() {
        val summary = inspect("""
            # Linux kernel configuration
            CONFIG_ENABLED=y
            CONFIG_MODULE=m
            # CONFIG_DISABLED is not set
            CONFIG_EXPLICIT_DISABLED=n
            CONFIG_COUNT=64
            CONFIG_OFFSET=-4
            CONFIG_MASK=0xFF
            CONFIG_LABEL="with spaces and \"quotes\""
            CONFIG_EMPTY=""
        """.trimIndent())
        assertEquals(9, summary.optionCount)
        assertEquals(1, summary.builtInCount)
        assertEquals(1, summary.moduleCount)
        assertEquals(2, summary.disabledCount)
        assertEquals(5, summary.otherCount)
        assertFalse(summary.compressed)
    }

    @Test
    fun digestUsesOriginalDecodedBytesAndGzipHasSameDigest() {
        val bytes = "CONFIG_TEST=y\n".toByteArray()
        val plain = inspector.inspect(bytes.inputStream())
        val gzip = inspector.inspect(compress(bytes).inputStream())
        assertEquals("e2ade8f3d471339eef89372a65cc46423a167d051cf6f0c0abbcc4076df1a500", plain.sha256)
        assertEquals(plain.sha256, gzip.sha256)
        assertEquals(bytes.size, gzip.decodedBytes)
        assertTrue(gzip.compressed)
    }

    @Test
    fun acceptsUtf8BomAndWindowsLineEndings() {
        val summary = inspect("\uFEFF# 配置\r\nCONFIG_TEST=y\r\n# CONFIG_OFF is not set\r\n")
        assertEquals(2, summary.optionCount)
        assertEquals(1, summary.disabledCount)
    }

    @Test
    fun rejectsDuplicateAndConflictingDefinitions() {
        listOf("CONFIG_TEST=y\nCONFIG_TEST=y", "CONFIG_TEST=y\n# CONFIG_TEST is not set").forEach {
            assertTrue(rejected(it).message!!.contains("重复"))
        }
    }

    @Test
    fun rejectsEmptyCommentsOnlyProfilesAndBinaryImages() {
        listOf("", "# comments only\n", "{\"profile\":\"test\"}", "PK\u0003\u0004", "CONFIG_TEST=y\u0000").forEach {
            rejected(it)
        }
    }

    @Test
    fun rejectsMalformedUtf8InsteadOfReplacingBytes() {
        assertThrows(ConfigValidationException::class.java) {
            inspector.inspect(byteArrayOf(0xc3.toByte(), 0x28).inputStream())
        }
    }

    @Test
    fun rejectsInvalidValuesAndTrailingCommands() {
        listOf("", "true", "yes", "1; id", "\"unterminated", "\"ok\" trailing", "$(id)", "\"dangling\\\"").forEach {
            rejected("CONFIG_TEST=$it")
        }
    }

    @Test
    fun acceptsLongQuotedValuesWithoutRecursiveMatching() {
        assertEquals(1, inspect("CONFIG_LABEL=\"" + "a".repeat(7000) + "\"").optionCount)
    }

    @Test
    fun rejectsDamagedOrTruncatedGzip() {
        val bytes = compress("CONFIG_TEST=y\n".toByteArray())
        val corrupt = bytes.copyOf().apply { this[size - 8] = (this[size - 8].toInt() xor 1).toByte() }
        listOf(bytes.copyOf(bytes.size - 5), corrupt).forEach {
            assertThrows(ConfigValidationException::class.java) { inspector.inspect(it.inputStream()) }
        }
    }

    @Test
    fun stopsReadingOversizedSourceAtLimitPlusOne() {
        var readCount = 0
        val input = object : InputStream() {
            override fun read(): Int { readCount++; return '#'.code }
        }
        assertThrows(ConfigValidationException::class.java) { inspector.inspect(input) }
        assertEquals(KernelConfigInspector.MAX_BYTES + 1, readCount)
    }

    @Test
    fun rejectsGzipExpansionBeyondLimit() {
        val bytes = ByteArray(KernelConfigInspector.MAX_BYTES + 1) { 'A'.code.toByte() }
        assertThrows(ConfigValidationException::class.java) { inspector.inspect(compress(bytes).inputStream()) }
    }

    @Test
    fun acceptsExactlyTheSizeLimit() {
        val prefix = "CONFIG_TEST=y\n"
        val filler = "#" + " ".repeat(1022) + "\n"
        val remaining = KernelConfigInspector.MAX_BYTES - prefix.length
        val text = prefix + filler.repeat(remaining / filler.length) + "#".repeat(remaining % filler.length)
        assertEquals(KernelConfigInspector.MAX_BYTES, inspect(text).decodedBytes)
    }

    @Test
    fun rejectsExcessiveLineLengthAndOptionCount() {
        rejected("CONFIG_TEST=\"" + "a".repeat(8192) + "\"")
        assertTrue(rejected((0..50_000).joinToString("\n") { "CONFIG_TEST_$it=y" }).message!!.contains("数量"))
    }

    private fun inspect(text: String) = inspector.inspect(text.byteInputStream())

    private fun rejected(text: String) = assertThrows(ConfigValidationException::class.java) { inspect(text) }

    private fun compress(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }
}

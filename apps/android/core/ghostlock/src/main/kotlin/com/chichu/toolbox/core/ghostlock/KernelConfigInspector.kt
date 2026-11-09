package com.chichu.toolbox.core.ghostlock

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

data class KernelConfigSummary(
    val sha256: String,
    val decodedBytes: Int,
    val compressed: Boolean,
    val optionCount: Int,
    val builtInCount: Int,
    val moduleCount: Int,
    val disabledCount: Int,
    val otherCount: Int,
)

class ConfigValidationException(message: String) : IOException(message)

/** Inspects a standalone Linux .config, not an execution profile or a device support assertion. */
class KernelConfigInspector {
    fun inspect(input: InputStream): KernelConfigSummary {
        // Bound both the source and expanded payload. Never extract an archive or evaluate input.
        val source = input.readBounded(MAX_BYTES)
        val compressed = source.size >= 2 && source[0] == 0x1f.toByte() && source[1] == 0x8b.toByte()
        val decoded = if (compressed) {
            try {
                GZIPInputStream(source.inputStream()).use { it.readBounded(MAX_BYTES) }
            } catch (error: ConfigValidationException) {
                throw error
            } catch (_: IOException) {
                throw ConfigValidationException("gzip 配置损坏或不完整")
            }
        } else source
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded)).toString().removePrefix("\uFEFF")
        } catch (_: CharacterCodingException) {
            throw ConfigValidationException("配置不是有效的 UTF-8 文本")
        }
        if (text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) {
            throw ConfigValidationException("配置包含二进制或控制字符")
        }
        val options = linkedMapOf<String, String>()
        text.lineSequence().forEachIndexed { index, raw ->
            if (raw.length > MAX_LINE_LENGTH) throw ConfigValidationException("第 ${index + 1} 行过长")
            val line = raw.trim()
            val disabled = notSet.matchEntire(line)
            val assignment = option.matchEntire(line)
            val pair = when {
                disabled != null -> disabled.groupValues[1] to "n"
                assignment != null -> {
                    val value = assignment.groupValues[2]
                    if (!isValidValue(value)) throw ConfigValidationException("第 ${index + 1} 行的配置值无效")
                    assignment.groupValues[1] to value
                }
                line.isEmpty() || line.startsWith('#') -> null
                else -> throw ConfigValidationException("第 ${index + 1} 行不是内核配置项")
            }
            if (pair != null) {
                if (options.put(pair.first, pair.second) != null) {
                    throw ConfigValidationException("第 ${index + 1} 行存在重复配置项 ${pair.first}")
                }
                if (options.size > MAX_OPTIONS) throw ConfigValidationException("配置项数量超过上限")
            }
        }
        if (options.isEmpty()) throw ConfigValidationException("文件中没有内核配置项")
        val counts = options.values.groupingBy { it }.eachCount()
        val builtIn = counts["y"] ?: 0
        val modules = counts["m"] ?: 0
        val disabled = counts["n"] ?: 0
        return KernelConfigSummary(
            sha256 = MessageDigest.getInstance("SHA-256").digest(decoded).joinToString("") { "%02x".format(it) },
            decodedBytes = decoded.size,
            compressed = compressed,
            optionCount = options.size,
            builtInCount = builtIn,
            moduleCount = modules,
            disabledCount = disabled,
            otherCount = options.size - builtIn - modules - disabled,
        )
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
            if (count < 0) return output.toByteArray()
            if (count == 0) continue
            if (output.size() + count > limit) throw ConfigValidationException("配置或解压后的文本超过 2 MiB 上限")
            output.write(buffer, 0, count)
        }
    }

    private fun isValidValue(value: String): Boolean {
        if (scalarValue.matches(value)) return true
        if (value.length < 2 || value.first() != '"' || value.last() != '"') return false
        // Scan quoted strings without a recursive regex, even for very long paths/labels.
        var index = 1
        while (index < value.lastIndex) {
            when (value[index]) {
                '"' -> return false
                '\\' -> {
                    index++
                    if (index >= value.lastIndex) return false
                }
            }
            index++
        }
        return true
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        private const val MAX_LINE_LENGTH = 8192
        private const val MAX_OPTIONS = 50_000
        private val notSet = Regex("# (CONFIG_[A-Za-z0-9_]+) is not set")
        private val option = Regex("(CONFIG_[A-Za-z0-9_]+)=(.*)")
        private val scalarValue = Regex("(?:[ymn]|-?[0-9]+|0[xX][0-9a-fA-F]+)")
    }
}

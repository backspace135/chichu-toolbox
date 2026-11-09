package com.chichu.toolbox.core.ksu

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Exact UAPI v6 / app_profile v4 layout. Keep unused bytes from the kernel. */
class AppPolicy private constructor(private val data: ByteArray, val configured: Boolean = true) {
    private fun buffer() = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    val allowed get() = data[264].toInt() != 0
    val useDefault get() = data[272].toInt() != 0
    val uid get() = buffer().getInt(536)
    val gid get() = buffer().getInt(540)
    val namespace get() = buffer().getInt(768)
    val umount get() = data[273].toInt() != 0
    val groups get() = List(buffer().getInt(544)) { buffer().getInt(548 + it * 4) }
    val capabilities get() = buffer().getLong(680)
    val context get() = data.copyOfRange(704, 768).takeWhile { it.toInt() != 0 }.toByteArray().toString(Charsets.UTF_8)
    val flags get() = buffer().getLong(776)
    val currentUid get() = buffer().getInt(260)
    val packageName get() = data.copyOfRange(4, 260).takeWhile { it.toInt() != 0 }.toByteArray().toString(Charsets.UTF_8)
    fun encode() = data.copyOf()

    fun bind(packageName: String, uid: Int): AppPolicy = AppPolicy(data.copyOf()).apply {
        val key = packageName.toByteArray()
        require(key.size < 256) { "包名超过 KernelSU 限额" }
        data.fill(0, 4, 260)
        key.copyInto(data, 4)
        buffer().putInt(260, uid)
    }

    fun grant(allow: Boolean): AppPolicy = AppPolicy(data.copyOf()).apply {
        // root and non-root share a union; never interpret old non-root bytes as credentials.
        if (allow != allowed) data.fill(0, 272)
        data[264] = if (allow) 1 else 0
        data[272] = 1
        // profile_valid checks the domain even when use_default is true.
        if (allow) initializeRoot()
    }

    fun custom(uid: Int, gid: Int, namespace: Int): AppPolicy = AppPolicy(data.copyOf()).apply {
        val initialize = !allowed || useDefault
        if (initialize) data.fill(0, 272)
        data[264] = 1
        data[272] = 0
        buffer().putInt(536, uid).putInt(540, gid).putInt(768, namespace)
        // A new custom policy begins with the standard KernelSU root credentials.
        if (initialize) {
            initializeRoot()
        }
    }

    private fun initializeRoot() {
        buffer().putInt(544, 1).putInt(548, 0)
        buffer().putLong(680, 0x1ffffffffffL).putLong(688, 0x1ffffffffffL)
        data.fill(0, 704, 768)
        "u:r:ksu:s0".toByteArray().copyInto(data, 704)
        buffer().putLong(776, 1L)
    }

    fun credentials(groups: List<Int>, capabilities: Long, context: String, flags: Long): AppPolicy = AppPolicy(data.copyOf()).apply {
        data.fill(0, 548, 680)
        buffer().putInt(544, groups.size)
        groups.forEachIndexed { index, gid -> buffer().putInt(548 + index * 4, gid) }
        buffer().putLong(680, capabilities).putLong(688, capabilities).putLong(696, 0)
        data.fill(0, 704, 768)
        context.toByteArray().copyInto(data, 704)
        buffer().putLong(776, flags)
    }

    fun template(): PolicyTemplate = PolicyTemplate(uid, gid, namespace, groups, capabilities, context, flags)

    fun apply(template: PolicyTemplate): AppPolicy = custom(template.uid, template.gid, template.namespace)
        .credentials(template.groups, template.capabilities, template.context, template.flags)

    fun deny(umount: Boolean): AppPolicy = grant(false).apply {
        data[272] = 0
        data[273] = if (umount) 1 else 0
    }

    companion object {
        fun absent(packageName: String, uid: Int): AppPolicy {
            val bytes = ByteArray(784)
            val key = packageName.toByteArray(Charsets.UTF_8)
            require(key.size < 256) { "包名超过 KernelSU 限额" }
            key.copyInto(bytes, 4)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 4).putInt(260, uid)
            bytes[272] = 1
            return AppPolicy(bytes, configured = false)
        }

        fun decode(bytes: ByteArray): AppPolicy {
            if (bytes.size != 784) throw IOException("KernelSU app_profile 长度错误：${bytes.size}")
            val version = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
            if (version != 4) throw IOException("不支持的 app_profile 版本：$version")
            if (bytes[264].toInt() !in 0..1 || bytes[272].toInt() !in 0..1) throw IOException("内核策略布尔值错误")
            if (bytes[264].toInt() == 1 && bytes[272].toInt() == 0) {
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                if (buffer.getInt(768) !in 0..2) throw IOException("不支持的挂载命名空间：${buffer.getInt(768)}")
                if (buffer.getInt(544) !in 0..32) throw IOException("内核策略组数量错误")
            }
            return AppPolicy(bytes.copyOf())
        }
    }
}

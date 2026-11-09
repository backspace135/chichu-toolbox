package com.chichu.toolbox.core.ksu

import com.google.gson.JsonParser
import com.google.gson.JsonParseException
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

object KsuProtocol {
    fun kernelInfo(text: String): KernelStatus {
        val entries = text.lineSequence().filter { it.isNotBlank() }.map {
            val split = it.indexOf(':')
            if (split < 1) throw IOException("ksud debug info 行格式错误：$it")
            it.substring(0, split) to it.substring(split + 1).trim()
        }.toList()
        if (entries.map { it.first }.distinct().size != entries.size) throw IOException("ksud 内核信息字段重复")
        val values = entries.toMap()
        fun number(key: String, radix: Int = 10): Int {
            val textValue = values[key] ?: throw IOException("ksud 内核信息缺少 $key")
            val digits = if (radix == 16 && textValue.startsWith("0x")) textValue.drop(2) else textValue
            return digits.toIntOrNull(radix) ?: throw IOException("ksud 内核信息 $key 数值错误")
        }
        val version = number("version")
        if (version <= 0) throw IOException("已有 Root，但 ksud 未检测到 KernelSU 内核")
        return KernelStatus(0, version, number("flags", 16), number("uapi_version"))
    }

    fun existingAccess(kernel: KernelStatus): RootAccess = when (kernel.uapi) {
        5 -> RootAccess.ExistingV5
        6 -> RootAccess.ExistingV6
        else -> throw IOException("已有 Root；当前 KernelSU UAPI ${kernel.uapi} 没有匹配的内置 ksud，模块操作已禁用")
    }
    fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"

    fun moduleId(value: String): String {
        if (!Regex("[a-zA-Z][a-zA-Z0-9._-]*").matches(value)) throw IOException("非法模块 ID：$value")
        return value
    }

    fun modules(text: String): List<RootModule> {
        try {
            val root = JsonParser.parseString(text)
            if (!root.isJsonArray) throw IOException("ksud module list 不是 JSON 数组")
            return root.asJsonArray.map { item ->
                if (!item.isJsonObject) throw IOException("模块记录不是 JSON 对象")
                val obj = item.asJsonObject
                fun string(key: String): String {
                    val value = obj.get(key) ?: throw IOException("模块记录缺少 $key")
                    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw IOException("模块 $key 类型错误")
                    return value.asString
                }
                fun flag(key: String): Boolean = when (val value = string(key)) {
                    "true" -> true
                    "false" -> false
                    else -> throw IOException("模块 $key 值错误：$value")
                }
                RootModule(moduleId(string("id")), string("name"), string("version"), string("description"),
                    flag("enabled"), flag("update"), flag("remove"), flag("web"), flag("action"))
            }.also { modules ->
                if (modules.map { it.id }.distinct().size != modules.size) throw IOException("模块 ID 重复")
            }
        } catch (error: JsonParseException) { throw IOException("ksud 返回无效 JSON", error) }
    }

    // One isolated origin per WebView. Remote documents, frames and scripts are blocked.
    fun webHost(id: String): String = "m-" + MessageDigest.getInstance("SHA-256")
        .digest(moduleId(id).toByteArray()).joinToString("") { "%02x".format(it) }.take(32) + ".chichu.local"

    fun webPath(url: String, host: String): String? {
        val uri = try { URI(url) } catch (_: java.net.URISyntaxException) { return null }
        if (uri.scheme != "https" || uri.host != host || uri.port != -1 || uri.rawUserInfo != null) return null
        val path = uri.path
        if (!path.startsWith("/") || path.contains('\\') || path.contains('\u0000')) return null
        if (path.split('/').any { it == ".." || it == "." }) return null
        return path.removePrefix("/").ifEmpty { "index.html" }
    }

    fun bridgeAllowed(origin: String, mainFrame: Boolean, active: Boolean, host: String): Boolean =
        active && mainFrame && origin == "https://$host"
}

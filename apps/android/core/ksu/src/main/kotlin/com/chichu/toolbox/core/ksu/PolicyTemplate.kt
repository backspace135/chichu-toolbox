package com.chichu.toolbox.core.ksu

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.IOException

data class PolicyTemplate(val uid: Int, val gid: Int, val namespace: Int, val groups: List<Int>,
    val capabilities: Long, val context: String, val flags: Long) {
    fun serialize(): String = """{"uid":$uid,"gid":$gid,"namespace":$namespace,"groups":[${groups.joinToString()}],"capabilities":$capabilities,"context":"$context","flags":$flags}"""

    companion object {
        fun parse(text: String): PolicyTemplate {
            try {
                val root = JsonParser.parseString(text)
                if (!root.isJsonObject) throw IOException("模板必须为 JSON 对象")
                val obj = root.asJsonObject
                fun integer(name: String): Long {
                    val value = obj.get(name) ?: throw IOException("模板缺少 $name")
                    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) throw IOException("模板 $name 必须为整数")
                    return value.asString.toLongOrNull() ?: throw IOException("模板 $name 不是有效整数")
                }
                val uid = integer("uid")
                val gid = integer("gid")
                val namespace = integer("namespace")
                val caps = integer("capabilities")
                val flags = integer("flags")
                val groupArray = obj.get("groups") ?: throw IOException("模板缺少 groups")
                if (!groupArray.isJsonArray) throw IOException("groups 必须为整数数组")
                val groups = groupArray.asJsonArray.map {
                    if (!it.isJsonPrimitive || !it.asJsonPrimitive.isNumber) throw IOException("groups 必须为整数数组")
                    it.asString.toIntOrNull()?.takeIf { gid -> gid >= 0 } ?: throw IOException("groups 必须为非负整数")
                }
                val domain = obj.get("context") ?: throw IOException("模板缺少 context")
                if (!domain.isJsonPrimitive || !domain.asJsonPrimitive.isString) throw IOException("context 必须为 SELinux 域字符串")
                if (uid !in 0L..Int.MAX_VALUE.toLong() || gid !in 0L..Int.MAX_VALUE.toLong()) throw IOException("UID / GID 必须为非负整数")
                if (namespace !in 0..2) throw IOException("namespace 必须为 0、1 或 2")
                if (groups.size > 32) throw IOException("最多支持 32 个附加组")
                if (caps < 0 || caps and 0x1ffffffffffL.inv() != 0L) throw IOException("capabilities 仅支持 Linux 0–40 位")
                if (flags !in 0..1) throw IOException("flags 仅支持 NO_NEW_PRIVS 位")
                val context = domain.asString
                if (!Regex("u:r:[a-zA-Z0-9_]+:s0").matches(context) || context.toByteArray().size >= 64) throw IOException("无效的 SELinux 域（需要 u:r:类型:s0，少于 64 字节）")
                return PolicyTemplate(uid.toInt(), gid.toInt(), namespace.toInt(), groups, caps, context, flags)
            } catch (error: JsonParseException) { throw IOException("模板 JSON 格式错误", error) }
        }
    }
}

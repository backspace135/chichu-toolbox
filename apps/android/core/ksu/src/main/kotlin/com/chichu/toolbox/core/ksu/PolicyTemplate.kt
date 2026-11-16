package com.chichu.toolbox.core.ksu

import com.google.gson.JsonArray
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.IOException

/**
 * One superuser profile template. [rules] holds the per-app SELinux rules that the
 * upstream manager stores via ksud (`profile get/set-sepolicy`), not in the kernel
 * app_profile; applying a template sets both surfaces like upstream does.
 */
data class PolicyTemplate(val uid: Int, val gid: Int, val namespace: Int, val groups: List<Int>,
    val capabilities: Long, val context: String, val flags: Long, val rules: String = "") {
    fun serialize(): String =
        """{"uid":$uid,"gid":$gid,"namespace":$namespace,"groups":[${groups.joinToString()}],""" +
            """"capabilities":$capabilities,"context":"$context","flags":$flags,"rules":${escape(rules)}}"""

    private fun escape(value: String): String = buildString {
        append('"')
        for (character in value) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    companion object {
        // Upstream template JSON uses Android group names and Linux capability names.
        // Mirrors me.weishu.kernelsu.profile.Groups; unknown names are skipped like upstream.
        private val UPSTREAM_GROUPS = mapOf(
            "ROOT" to 0, "DAEMON" to 1, "BIN" to 2, "SYS" to 3,
            "SYSTEM" to 1000, "RADIO" to 1001, "BLUETOOTH" to 1002, "GRAPHICS" to 1003, "INPUT" to 1004,
            "AUDIO" to 1005, "CAMERA" to 1006, "LOG" to 1007, "COMPASS" to 1008, "MOUNT" to 1009,
            "WIFI" to 1010, "ADB" to 1011, "INSTALL" to 1012, "MEDIA" to 1013, "DHCP" to 1014,
            "SDCARD_RW" to 1015, "VPN" to 1016, "KEYSTORE" to 1017, "USB" to 1018, "DRM" to 1019,
            "MDNSR" to 1020, "GPS" to 1021, "MEDIA_RW" to 1023, "MTP" to 1024, "DRMRPC" to 1026,
            "NFC" to 1027, "SDCARD_R" to 1028, "CLAT" to 1029, "LOOP_RADIO" to 1030, "MEDIA_DRM" to 1031,
            "PACKAGE_INFO" to 1032, "SDCARD_PICS" to 1033, "SDCARD_AV" to 1034, "SDCARD_ALL" to 1035,
            "LOGD" to 1036, "SHARED_RELRO" to 1037, "DBUS" to 1038, "TLSDATE" to 1039, "MEDIA_EX" to 1040,
            "AUDIOSERVER" to 1041, "METRICS_COLL" to 1042, "METRICSD" to 1043, "WEBSERV" to 1044,
            "DEBUGGERD" to 1045, "MEDIA_CODEC" to 1046, "CAMERASERVER" to 1047, "FIREWALL" to 1048,
            "TRUNKS" to 1049, "NVRAM" to 1050, "DNS" to 1051, "DNS_TETHER" to 1052, "WEBVIEW_ZYGOTE" to 1053,
            "VEHICLE_NETWORK" to 1054, "MEDIA_AUDIO" to 1055, "MEDIA_VIDEO" to 1056, "MEDIA_IMAGE" to 1057,
            "TOMBSTONED" to 1058, "MEDIA_OBB" to 1059, "ESE" to 1060, "OTA_UPDATE" to 1061,
            "AUTOMOTIVE_EVS" to 1062, "LOWPAN" to 1063, "HSM" to 1064, "RESERVED_DISK" to 1065,
            "STATSD" to 1066, "INCIDENTD" to 1067, "SECURE_ELEMENT" to 1068, "LMKD" to 1069, "LLKD" to 1070,
            "IORAPD" to 1071, "GPU_SERVICE" to 1072, "NETWORK_STACK" to 1073, "GSID" to 1074,
            "FSVERITY_CERT" to 1075, "CREDSTORE" to 1076, "EXTERNAL_STORAGE" to 1077, "EXT_DATA_RW" to 1078,
            "EXT_OBB_RW" to 1079, "CONTEXT_HUB" to 1080, "VIRTUALIZATIONSERVICE" to 1081, "ARTD" to 1082,
            "UWB" to 1083, "THREAD_NETWORK" to 1084, "DICED" to 1085, "DMESGD" to 1086, "JC_WEAVER" to 1087,
            "JC_STRONGBOX" to 1088, "JC_IDENTITYCRED" to 1089, "SDK_SANDBOX" to 1090,
            "SECURITY_LOG_WRITER" to 1091, "PRNG_SEEDER" to 1092, "UPROBESTATS" to 1093,
            "CROS_EC" to 1094, "MMD" to 1095, "SHELL" to 2000)

        private val UPSTREAM_CAPABILITIES = mapOf(
            "CAP_CHOWN" to 0L, "CAP_DAC_OVERRIDE" to 1L, "CAP_DAC_READ_SEARCH" to 2L, "CAP_FOWNER" to 3L,
            "CAP_FSETID" to 4L, "CAP_KILL" to 5L, "CAP_SETGID" to 6L, "CAP_SETUID" to 7L, "CAP_SETPCAP" to 8L,
            "CAP_LINUX_IMMUTABLE" to 9L, "CAP_NET_BIND_SERVICE" to 10L, "CAP_NET_BROADCAST" to 11L,
            "CAP_NET_ADMIN" to 12L, "CAP_NET_RAW" to 13L, "CAP_IPC_LOCK" to 14L, "CAP_IPC_OWNER" to 15L,
            "CAP_SYS_MODULE" to 16L, "CAP_SYS_RAWIO" to 17L, "CAP_SYS_CHROOT" to 18L, "CAP_SYS_PTRACE" to 19L,
            "CAP_SYS_PACCT" to 20L, "CAP_SYS_ADMIN" to 21L, "CAP_SYS_BOOT" to 22L, "CAP_SYS_NICE" to 23L,
            "CAP_SYS_RESOURCE" to 24L, "CAP_SYS_TIME" to 25L, "CAP_SYS_TTY_CONFIG" to 26L, "CAP_MKNOD" to 27L,
            "CAP_LEASE" to 28L, "CAP_AUDIT_WRITE" to 29L, "CAP_AUDIT_CONTROL" to 30L, "CAP_SETFCAP" to 31L,
            "CAP_MAC_OVERRIDE" to 32L, "CAP_MAC_ADMIN" to 33L, "CAP_SYSLOG" to 34L, "CAP_WAKE_ALARM" to 35L,
            "CAP_BLOCK_SUSPEND" to 36L, "CAP_AUDIT_READ" to 37L, "CAP_PERFMON" to 38L, "CAP_BPF" to 39L,
            "CAP_CHECKPOINT_RESTORE" to 40L)

        private fun integer(obj: com.google.gson.JsonObject, name: String): Long {
            val value = obj.get(name) ?: throw IOException("模板缺少 $name")
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) throw IOException("模板 $name 必须为整数")
            return value.asString.toLongOrNull() ?: throw IOException("模板 $name 不是有效整数")
        }

        fun parse(text: String): PolicyTemplate {
            try {
                val root = JsonParser.parseString(text)
                if (!root.isJsonObject) throw IOException("模板必须为 JSON 对象")
                val obj = root.asJsonObject
                val uid = integer(obj, "uid")
                val gid = integer(obj, "gid")
                val namespace = integer(obj, "namespace")
                val caps = integer(obj, "capabilities")
                val flags = integer(obj, "flags")
                val groupArray = obj.get("groups") ?: throw IOException("模板缺少 groups")
                if (!groupArray.isJsonArray) throw IOException("groups 必须为整数数组")
                val groups = groupArray.asJsonArray.map {
                    if (!it.isJsonPrimitive || !it.asJsonPrimitive.isNumber) throw IOException("groups 必须为整数数组")
                    it.asString.toIntOrNull()?.takeIf { gid -> gid >= 0 } ?: throw IOException("groups 必须为非负整数")
                }
                val domain = obj.get("context") ?: throw IOException("模板缺少 context")
                if (!domain.isJsonPrimitive || !domain.asJsonPrimitive.isString) throw IOException("context 必须为 SELinux 域字符串")
                val rules = obj.get("rules")?.let {
                    if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) throw IOException("rules 必须为字符串")
                    it.asString
                } ?: ""
                if (uid !in 0L..Int.MAX_VALUE.toLong() || gid !in 0L..Int.MAX_VALUE.toLong()) throw IOException("UID / GID 必须为非负整数")
                if (namespace !in 0..2) throw IOException("namespace 必须为 0、1 或 2")
                if (groups.size > 32) throw IOException("最多支持 32 个附加组")
                if (caps < 0 || caps and 0x1ffffffffffL.inv() != 0L) throw IOException("capabilities 仅支持 Linux 0–40 位")
                if (flags !in 0..1) throw IOException("flags 仅支持 NO_NEW_PRIVS 位")
                if (rules.toByteArray().size >= 8192) throw IOException("SELinux 规则过长（少于 8 KiB）")
                val context = domain.asString
                if (!Regex("u:r:[a-zA-Z0-9_]+:s0").matches(context) || context.toByteArray().size >= 64) throw IOException("无效的 SELinux 域（需要 u:r:类型:s0，少于 64 字节）")
                return PolicyTemplate(uid.toInt(), gid.toInt(), namespace.toInt(), groups, caps, context, flags, rules)
            } catch (error: JsonParseException) { throw IOException("模板 JSON 格式错误", error) }
        }

        /**
         * Parse an original ksud template-library entry (the upstream manager format:
         * Android group names, CAP_* capability names, named flags). Unknown names are
         * skipped exactly like upstream; the result goes through the same [parse]
         * validation as a toolbox template.
         */
        fun fromUpstream(text: String): PolicyTemplate {
            val root = try { JsonParser.parseString(text) } catch (error: JsonParseException) {
                throw IOException("ksud 模板 JSON 格式错误", error)
            }
            if (!root.isJsonObject) throw IOException("ksud 模板必须为 JSON 对象")
            val obj = root.asJsonObject
            val converted = com.google.gson.JsonObject()
            converted.addProperty("uid", obj.get("uid")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0)
            converted.addProperty("gid", obj.get("gid")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0)
            converted.addProperty("namespace", when (obj.get("namespace")?.takeIf { it.isJsonPrimitive }?.asString?.uppercase()) {
                "GLOBAL" -> 1
                "ISOLATED" -> 2
                else -> 0
            })
            val groups = JsonArray()
            obj.get("groups")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { name ->
                UPSTREAM_GROUPS[name.asString]?.let(groups::add)
            }
            converted.add("groups", groups)
            var capabilities = 0L
            obj.get("capabilities")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { name ->
                UPSTREAM_CAPABILITIES[name.asString]?.let { capabilities = capabilities or (1L shl it.toInt()) }
            }
            converted.addProperty("capabilities", capabilities)
            converted.addProperty("context", obj.get("context")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() } ?: "u:r:ksu:s0")
            val flags = obj.get("flags")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.any { it.asString == "NO_NEW_PRIVS" } ?: true
            converted.addProperty("flags", if (flags) 1L else 0L)
            val rules = obj.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.asString.takeIf(String::isNotBlank) } ?: emptyList()
            converted.addProperty("rules", rules.joinToString("\n"))
            return parse(converted.toString())
        }
    }
}

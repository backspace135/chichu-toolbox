package com.chichu.toolbox.core.shizuku.activation

object ActivationInput {
    fun port(value: String): Int {
        require(value.isNotEmpty() && value.all { it in '0'..'9' }) { "端口必须为 1–65535 的整数" }
        return requireNotNull(value.toIntOrNull()?.takeIf { it in 1..65535 }) { "端口必须为 1–65535 的整数" }
    }

    fun pairingCode(value: String): String {
        require(value.length == 6 && value.all { it in '0'..'9' }) { "请输入六位数字配对码" }
        return value
    }
}

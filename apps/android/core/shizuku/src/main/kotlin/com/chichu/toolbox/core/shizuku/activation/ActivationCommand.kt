package com.chichu.toolbox.core.shizuku.activation

object ActivationCommand {
    fun create(nativeLibraryDir: String, apkPath: String): String {
        require(nativeLibraryDir.startsWith('/') && apkPath.startsWith('/')) {
            "启动器和 APK 必须使用绝对路径"
        }
        require('\u0000' !in nativeLibraryDir && '\u0000' !in apkPath)
        return "${quote("${nativeLibraryDir.trimEnd('/')}/libchichu_starter.so")} --apk=${quote(apkPath)}"
    }

    private fun quote(value: String) = "'${value.replace("'", "'\"'\"'")}'"
}

package com.chichu.toolbox.core.ksu

internal object KernelBridge {
    init { System.loadLibrary("chichu_ksu") }
    external fun probe(): IntArray
    external fun safeMode(): Boolean
    external fun profile(key: String, uid: Int): ByteArray
    external fun setProfile(bytes: ByteArray)
}

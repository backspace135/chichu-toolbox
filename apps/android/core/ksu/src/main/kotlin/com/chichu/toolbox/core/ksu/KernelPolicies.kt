package com.chichu.toolbox.core.ksu

import java.io.IOException

/** Both native and tests use the same write/readback transaction. No optimistic grants. */
internal class KernelPolicies(
    private val read: (String, Int) -> ByteArray,
    private val write: (ByteArray) -> Unit,
) {
    fun get(packageName: String, uid: Int): AppPolicy {
        val bytes = read(packageName, uid)
        return if (bytes.isEmpty()) AppPolicy.absent(packageName, uid) else AppPolicy.decode(bytes)
    }

    fun set(packageName: String, uid: Int, value: AppPolicy): AppPolicy {
        write(value.bind(packageName, uid).encode())
        val actual = get(packageName, uid)
        if (!actual.configured || actual.currentUid != uid || actual.packageName != packageName || actual.allowed != value.allowed || actual.useDefault != value.useDefault ||
            (value.allowed && !value.useDefault && actual.template() != value.template()) ||
            (!value.allowed && !value.useDefault && actual.umount != value.umount)) {
            throw IOException("内核读回策略与提交值不一致，请刷新检查")
        }
        return actual
    }
}

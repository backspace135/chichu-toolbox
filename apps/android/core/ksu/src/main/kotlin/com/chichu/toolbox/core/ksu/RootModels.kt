package com.chichu.toolbox.core.ksu

import java.io.IOException

const val KSU_SOURCE = "730f644615969cfd4a84b45d7f62a667b7b95a50"
const val KSU_UAPI = 6
const val KSU_V5_SOURCE = "0c0fdb960178fa12d80d6cbcfdcb6ad8be6a865d"

enum class RootAccess { Manager, ExistingV5, ExistingV6 }

enum class RootMode(val title: String) { BuiltIn("内建 / GKI"), Lkm("LKM"), Bundled("内嵌 LKM"), LateLoad("临时加载") }

data class KernelStatus(val errno: Int, val version: Int, val flags: Int, val uapi: Int) {
    val available get() = errno == 0
    val manager get() = available && flags and 2 != 0
    val compatible get() = available && uapi == KSU_UAPI
    val mode get() = when {
        flags and 4 != 0 -> RootMode.LateLoad
        flags and 16 != 0 -> RootMode.Bundled
        flags and 1 != 0 -> RootMode.Lkm
        else -> RootMode.BuiltIn
    }
    val managementIssue get() = when {
        !available -> "未获得 KernelSU 驱动通道（errno $errno）；可能未加载或当前应用未被内核识别，不能据此断言设备没有 Root。"
        !compatible -> "内核 UAPI $uapi 与工具箱 UAPI $KSU_UAPI 不一致，已禁用管理操作。"
        !manager -> "内核未将工具箱识别为管理器；普通 Root 授权不能替代管理器包名与签名校验。"
        else -> null
    }
}

data class RootSnapshot(
    val kernel: KernelStatus,
    val originalInstalled: Boolean,
    val certificateSha256: String,
    val daemonVersion: String,
    val daemonAvailable: Boolean,
    val access: RootAccess?,
    val observedKernel: KernelStatus?,
    val safeMode: Boolean?,
) {
    val canManage get() = kernel.managementIssue == null && access == RootAccess.Manager && daemonAvailable
    val canModules get() = canManage || access == RootAccess.ExistingV5 || access == RootAccess.ExistingV6
    val runningKernel get() = observedKernel ?: kernel
    val issue get() = if (canModules && !canManage) {
        "已有 Root 与模块通道可用；工具箱未取得内核管理器身份，超级用户策略仍由原版管理器负责。"
    } else kernel.managementIssue ?: when {
        !daemonAvailable -> "此 ABI 未包含固定版本 ksud，管理功能不可用。"
        access == null -> "管理器身份已识别，但 ksud Root 会话尚未验证。点击验证 Root 通道。"
        else -> null
    }
}

data class CommandResult(val code: Int, val output: String, val stderr: String = "") {
    val transcript get() = output + if (stderr.isNotEmpty()) "\n[stderr]\n$stderr" else ""
    fun requireSuccess(): String {
        if (code != 0) throw IOException("命令退出码 $code\n$transcript")
        return output
    }
}

data class RootApplication(val name: String, val packageName: String, val uid: Int, val profile: AppPolicy)
data class RootModule(val id: String, val name: String, val version: String, val description: String,
    val enabled: Boolean, val update: Boolean, val remove: Boolean, val web: Boolean, val action: Boolean)

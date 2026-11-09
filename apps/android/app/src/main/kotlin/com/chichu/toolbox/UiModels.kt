package com.chichu.toolbox

import com.chichu.toolbox.core.common.ServiceState
import com.chichu.toolbox.core.common.ShellGrant

data class ServicePresentation(val title: String, val detail: String)
fun ServiceState.presentation(): ServicePresentation = when (this) {
    ServiceState.Offline -> ServicePresentation("未激活", "通过无线调试或电脑 ADB 启动内置 Shell 服务")
    ServiceState.Activating -> ServicePresentation("正在连接", "正在验证内置服务身份")
    is ServiceState.Failed -> ServicePresentation("连接失败", message)
    is ServiceState.Connected -> ServicePresentation("已连接", "Shell · UID ${identity.uid} · 服务 ${identity.version}.${identity.patchVersion}")
}

data class ApplicationItem(val label: String, val packageName: String, val uid: Int, val grant: ShellGrant)
fun List<ApplicationItem>.matching(query: String): List<ApplicationItem> = query.trim().let { value ->
    filter { value.isEmpty() || it.label.contains(value, true) || it.packageName.contains(value, true) }
}
val ShellGrant.label: String get() = when (this) {
    ShellGrant.Ask -> "未授权"
    ShellGrant.Allowed -> "已批准"
    ShellGrant.Denied -> "已拒绝"
}

data class ApplicationsState(
    val items: List<ApplicationItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val changingUid: Int? = null,
)
data class Appearance(val mode: String = "system", val dynamic: Boolean = false)

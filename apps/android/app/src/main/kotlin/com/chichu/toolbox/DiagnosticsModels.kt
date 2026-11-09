package com.chichu.toolbox

import com.chichu.toolbox.core.ghostlock.DeviceSnapshot
import com.chichu.toolbox.core.ghostlock.KernelConfigSummary

data class DiagnosticsState(
    val loading: Boolean = false,
    val snapshot: DeviceSnapshot? = null,
    val error: String? = null,
)

data class ConfigImportState(
    val loading: Boolean = false,
    val summary: KernelConfigSummary? = null,
    val error: String? = null,
)

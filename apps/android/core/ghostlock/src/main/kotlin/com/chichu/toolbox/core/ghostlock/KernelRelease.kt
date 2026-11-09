package com.chichu.toolbox.core.ghostlock

/** The complete release is retained; the numeric version alone is not a compatibility key. */
data class KernelRelease(
    val full: String,
    val major: Int,
    val minor: Int,
    val patch: Int,
    val suffix: String,
) {
    companion object {
        private val pattern = Regex("([0-9]+)\\.([0-9]+)\\.([0-9]+)([-+._a-zA-Z0-9]*)")

        fun parse(value: String): KernelRelease? {
            val match = pattern.matchEntire(value) ?: return null
            return KernelRelease(
                full = value,
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
                suffix = match.groupValues[4],
            )
        }
    }
}

package com.chichu.toolbox.core.ghostlock

import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.system.Os

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdk: Int,
    val securityPatch: String,
    val fingerprint: String,
    val supportedAbis: List<String>,
    val kernelRelease: String,
    val kernelVersion: String,
    val processUid: Int,
    val elapsedRealtimeMillis: Long,
) {
    val parsedKernel: KernelRelease? get() = KernelRelease.parse(kernelRelease)
}

/** Only observes the application's own process and public OS metadata. No shell or su is run. */
class DeviceDiagnostics {
    fun read(): DeviceSnapshot {
        val uname = Os.uname()
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            fingerprint = Build.FINGERPRINT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            kernelRelease = uname.release,
            kernelVersion = uname.version,
            processUid = Process.myUid(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
    }
}

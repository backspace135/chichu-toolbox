package com.chichu.toolbox.core.ksu

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset

class KsuRepository(private val context: Context) {
    val daemonPath: String = File(context.applicationInfo.nativeLibraryDir, "libksud.so").absolutePath
    private val operationLock = Mutex()
    private val journal = File(context.filesDir, "root-operations.log")
    private val policies = KernelPolicies(KernelBridge::profile, KernelBridge::setProfile)
    private val preferences = context.getSharedPreferences("root-policy-templates", Context.MODE_PRIVATE)
    private var access: RootAccess? = null
    private var observedKernel: KernelStatus? = null

    fun invalidate() { access = null; observedKernel = null }
    private fun selectedDaemon(): String = when (access) {
        RootAccess.Manager, RootAccess.ExistingV6 -> daemonPath
        RootAccess.ExistingV5 -> File(context.applicationInfo.nativeLibraryDir, "libksud_v5.so").absolutePath
        null -> throw IOException("Root 通道尚未验证，请先执行验证")
    }

    suspend fun snapshot(): RootSnapshot = withContext(Dispatchers.IO) {
        val values = KernelBridge.probe()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = checkNotNull(info.signingInfo) { "Installed APK has no signingInfo" }
        val certificate = signing.apkContentsSigners.joinToString("\n") {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }
        val original = try {
            context.packageManager.getPackageInfo("me.weishu.kernelsu", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
        val daemon = File(daemonPath).isFile
        val version = if (daemon) plain(listOf(daemonPath, "--version")).requireSuccess().trim() else "此 ABI 未打包 ksud"
        val kernel = KernelStatus(values[0], values[1], values[2], values[3])
        val safeMode = if (kernel.managementIssue == null) KernelBridge.safeMode() else null
        RootSnapshot(kernel, original, certificate, version, daemon, access, observedKernel, safeMode)
    }

    private suspend fun plain(args: List<String>, script: String = ""): CommandResult = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(args).start()
        try {
            coroutineScope {
                val output = async { runInterruptible { process.inputStream.bufferedReader().use { it.readText() } } }
                val stderr = async { runInterruptible { process.errorStream.bufferedReader().use { it.readText() } } }
                try {
                    process.outputStream.bufferedWriter().use { it.write(script) }
                    val code = runInterruptible { process.waitFor() }
                    CommandResult(code, output.await(), stderr.await())
                } finally {
                    process.destroy()
                    process.inputStream.close()
                    process.errorStream.close()
                }
            }
        } finally { process.destroy() }
    }

    private fun rootArguments(): List<String> = when (access) {
        RootAccess.Manager -> listOf(daemonPath, "debug", "su", "-g")
        RootAccess.ExistingV5, RootAccess.ExistingV6 -> listOf("su", "-c", "/system/bin/sh")
        null -> throw IOException("Root 通道尚未验证，请先执行验证")
    }
    private suspend fun root(command: String): CommandResult = plain(rootArguments(),
        "if [ \"\$(id -u)\" != 0 ]; then exit 126; fi\n$command\nexit\n")

    suspend fun verifyRoot(): CommandResult = operationLock.withLock {
        invalidate()
        val status = snapshot()
        status.kernel.managementIssue?.let { throw IOException(it) }
        val result = plain(listOf(daemonPath, "debug", "su", "-g"), "id -u\nexit\n")
        result.requireSuccess()
        if (result.output.trim() != "0") throw IOException("ksud 未返回真实 Root UID：${result.output}")
        access = RootAccess.Manager
        result
    }

    suspend fun applications(): List<RootApplication> = withContext(Dispatchers.IO) {
        snapshot().kernel.managementIssue?.let { throw IOException(it) }
        context.packageManager.getInstalledApplications(0)
            .filter { it.uid >= 10000 && it.packageName != context.packageName }
            .map { RootApplication(it.loadLabel(context.packageManager).toString(), it.packageName, it.uid, policies.get(it.packageName, it.uid)) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun setPolicy(app: RootApplication, value: AppPolicy) = operationLock.withLock {
        withContext(Dispatchers.IO) {
            snapshot().kernel.managementIssue?.let { throw IOException(it) }
            // Package removal / UID reuse is a real authorization boundary.
            val current = context.packageManager.getApplicationInfo(app.packageName, 0)
            if (current.uid != app.uid) throw IOException("应用 UID 已变更，请刷新后重新授权")
            val actual = policies.set(app.packageName, app.uid, value)
            appendJournal("${app.packageName} UID ${app.uid}：内核读回 ${if (actual.allowed) "允许 Root" else "不允许 Root"}；默认策略=${actual.useDefault}")
        }
    }

    suspend fun modules(): List<RootModule> {
        return KsuProtocol.modules(root("${KsuProtocol.quote(selectedDaemon())} module list").requireSuccess())
    }

    suspend fun command(arguments: List<String>): CommandResult = operationLock.withLock {
        val result = root((listOf(selectedDaemon()) + arguments).joinToString(" ", transform = KsuProtocol::quote))
        withContext(Dispatchers.IO) { appendJournal("ksud ${arguments.joinToString(" ")}\n退出码 ${result.code}\n${result.transcript}") }
        result
    }

    suspend fun installModule(uri: Uri): CommandResult = withContext(Dispatchers.IO) {
        val zip = File.createTempFile("module-", ".zip", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("无法打开模块文件")
            input.use { source -> zip.outputStream().use { source.copyTo(it) } }
            command(listOf("module", "install", zip.absolutePath))
        } finally { zip.delete() }
    }

    suspend fun logs(): String = withContext(Dispatchers.IO) {
        if (journal.exists()) journal.readText() else "尚无工具箱 Root 操作记录。内核调用日志需要已启用的 KernelSU sulog 功能。"
    }

    suspend fun verifyExistingRoot(): CommandResult = operationLock.withLock {
        invalidate()
        val result = plain(listOf("su", "-c", "id -u"))
        result.requireSuccess()
        if (result.output.trim() != "0") throw IOException("su 未返回 Root UID：${result.output}")
        val info = plain(listOf("su", "-c", "${KsuProtocol.quote(daemonPath)} debug info"))
        val kernel = KsuProtocol.kernelInfo(info.requireSuccess())
        val channel = KsuProtocol.existingAccess(kernel)
        val binary = when (channel) {
            RootAccess.ExistingV5 -> File(context.applicationInfo.nativeLibraryDir, "libksud_v5.so").absolutePath
            RootAccess.ExistingV6 -> daemonPath
            RootAccess.Manager -> error("existingAccess cannot return manager identity")
        }
        plain(listOf(binary, "--version")).requireSuccess()
        access = channel
        observedKernel = kernel
        CommandResult(0, "UID 0\n${info.output}\n匹配 ksud：${if (channel == RootAccess.ExistingV5) "3.3.1 / UAPI 5" else "3.3.1-38 / UAPI 6"}")
    }

    /** Only the upstream debug kernel exposes this migration mechanism. No signature bypass. */
    suspend fun migrateDebugManager(): CommandResult = operationLock.withLock {
        val parameter = "/sys/module/kernelsu/parameters/ksu_debug_manager_appid"
        val probe = plain(listOf("su", "-c", "test -w ${KsuProtocol.quote(parameter)}"))
        if (probe.code != 0) throw IOException("当前内核没有可写的 CONFIG_KSU_DEBUG 管理器迁移接口。正式内核需重新构建并绑定工具箱包名与签名，不能在线接管。")
        // ksud force-stops the target package. Write the intention before executing it.
        withContext(Dispatchers.IO) { appendJournal("用户确认：请求将 debug 内核管理器迁移到 ${context.packageName}；原管理器授权数据库不复制、不删除。") }
        plain(listOf("su", "-c", "${KsuProtocol.quote(daemonPath)} debug set-manager ${KsuProtocol.quote(context.packageName)}"))
    }

    suspend fun kernelLogs(): String {
        return root("/system/bin/ls -l /data/adb/ksu/log && /system/bin/cat /data/adb/ksu/log/*").requireSuccess()
    }

    suspend fun webAsset(moduleId: String, relative: String): ByteArray {
        return withContext(Dispatchers.IO) {
            val base = "/data/adb/modules/${KsuProtocol.moduleId(moduleId)}/webroot"
            val path = "$base/$relative"
            // Resolve symlinks as root and require containment before serving privileged assets.
            val command = "[ \"\$(id -u)\" = 0 ] || exit 126; base=${KsuProtocol.quote(base)}; " +
                "[ \"\$(realpath \"\$base\")\" = \"\$base\" ] || exit 13; path=\$(realpath ${KsuProtocol.quote(path)}) || exit; " +
                "case \"\$path\" in \"\$base\"/*) /system/bin/cat \"\$path\";; *) exit 13;; esac"
            val process = ProcessBuilder(rootArguments()).start()
            try {
                coroutineScope {
                    val bytes = async { runInterruptible { process.inputStream.use { it.readBytes() } } }
                    val error = async { runInterruptible { process.errorStream.bufferedReader().use { it.readText() } } }
                    try {
                        process.outputStream.bufferedWriter().use { it.write("$command\nexit\n") }
                        val code = runInterruptible { process.waitFor() }
                        if (code != 0) throw IOException("WebUI 文件读取失败（$code）：${error.await()}")
                        bytes.await()
                    } finally {
                        process.destroy()
                        process.inputStream.close()
                        process.errorStream.close()
                    }
                }
            } finally { process.destroy() }
        }
    }

    suspend fun webExec(moduleId: String, command: String): CommandResult = operationLock.withLock {
        selectedDaemon()
        root("cd ${KsuProtocol.quote("/data/adb/modules/${KsuProtocol.moduleId(moduleId)}")} && $command")
    }

    suspend fun templates(): Map<String, PolicyTemplate> = withContext(Dispatchers.IO) {
        preferences.all.mapValues { (_, value) -> PolicyTemplate.parse(value as String) }
    }

    suspend fun saveTemplate(name: String, template: PolicyTemplate) = withContext(Dispatchers.IO) {
        if (name.isBlank() || name.length > 80) throw IOException("模板名称需为 1–80 字符")
        if (!preferences.edit().putString(name, template.serialize()).commit()) throw IOException("模板保存失败")
    }

    suspend fun deleteTemplate(name: String) = withContext(Dispatchers.IO) {
        if (!preferences.edit().remove(name).commit()) throw IOException("模板删除失败")
    }

    suspend fun recordFailure(message: String) = withContext(Dispatchers.IO) {
        appendJournal("操作失败：$message")
    }

    private fun appendJournal(text: String) {
        journal.appendText("${OffsetDateTime.now(ZoneOffset.ofHours(9))}\n$text\n\n")
    }
}

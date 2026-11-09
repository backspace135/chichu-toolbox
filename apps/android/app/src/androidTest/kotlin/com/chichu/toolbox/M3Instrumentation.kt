package com.chichu.toolbox

import android.app.Instrumentation
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import com.chichu.toolbox.core.ksu.KsuRepository
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

/** Explicit, one-operation device harness. Not included in the production APK. */
class M3Instrumentation : Instrumentation() {
    private lateinit var arguments: Bundle
    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)
        this.arguments = arguments
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking {
                val repository = KsuRepository(targetContext)
                result.putString("root", repository.verifyExistingRoot().output)
                val id = "chichu_m3_acceptance"
                val operation = arguments.getString("operation") ?: "list"
                when (operation) {
                    "list" -> result.putString("modules", repository.modules().joinToString { "${it.id}:${it.version}:enabled=${it.enabled}:remove=${it.remove}:web=${it.web}:action=${it.action}" })
                    "install" -> {
                        val version = arguments.getString("version") ?: error("version required")
                        check(version == "1" || version == "2") { "Only acceptance fixture versions are allowed" }
                        val file = File(targetContext.cacheDir, "chichu-m3-v$version.zip")
                        val input = context.assets.open("root-fixture/chichu-m3-v$version.zip")
                        input.use { source -> file.outputStream().use { source.copyTo(it) } }
                        // Installation is performed by the target app's repository and UID.
                        try {
                            val command = repository.installModule(Uri.fromFile(file))
                            result.putString("command", command.transcript)
                            command.requireSuccess()
                        } finally { file.delete() }
                    }
                    "enable", "disable", "action", "uninstall", "undo-uninstall" -> {
                        val command = repository.command(listOf("module", operation, id))
                        result.putString("command", command.transcript)
                        command.requireSuccess()
                    }
                    "asset" -> result.putString("asset", repository.webAsset(id, "index.html").toString(Charsets.UTF_8))
                    "escape" -> {
                        try {
                            repository.webAsset(id, "../module.prop")
                            error("WebUI asset escaped webroot")
                        } catch (expected: IOException) { result.putString("escape", "BLOCKED: ${expected.message}") }
                    }
                    "exec" -> {
                        val command = repository.webExec(id, "id; printf WEBUI_OK")
                        command.requireSuccess()
                        check("uid=0(root)" in command.output && "WEBUI_OK" in command.output)
                        result.putString("command", command.transcript)
                    }
                    else -> error("Unsupported harness operation: $operation")
                }
                result.putString("after", repository.modules().joinToString { "${it.id}:${it.version}:enabled=${it.enabled}:remove=${it.remove}:update=${it.update}" })
            }
            finish(Activity.RESULT_OK, result)
        } catch (failure: IOException) {
            result.putString("error", failure.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}

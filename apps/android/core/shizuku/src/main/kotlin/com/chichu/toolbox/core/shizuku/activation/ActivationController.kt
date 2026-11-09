package com.chichu.toolbox.core.shizuku.activation

import android.content.Context
import com.chichu.toolbox.core.common.ServiceState
import com.chichu.toolbox.core.shizuku.ShizukuRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbPairingClient
import moe.shizuku.manager.adb.PreferenceAdbKeyStore

enum class ActivationStage { Idle, Pairing, Paired, Connecting, Starting, WaitingBinder, Connected, Canceled, Failed }
data class ActivationStatus(val stage: ActivationStage = ActivationStage.Idle, val message: String = "", val log: String = "") {
    val busy get() = stage in setOf(ActivationStage.Pairing, ActivationStage.Connecting, ActivationStage.Starting, ActivationStage.WaitingBinder)
}

class ActivationController(context: Context, private val repository: ShizukuRepository, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val gate = ActivationGate()
    private var job: Job? = null
    private val mutableStatus = MutableStateFlow(ActivationStatus())
    val status = mutableStatus.asStateFlow()
    private val key by lazy {
        AdbKey(PreferenceAdbKeyStore(app.getSharedPreferences("wireless_adb", Context.MODE_PRIVATE)), "chichu@toolbox")
    }

    fun command(): String = app.applicationInfo.let { ActivationCommand.create(it.nativeLibraryDir, it.sourceDir) }
    fun computerCommand() = "adb shell ${command()}"

    fun pair(port: String, code: String) = startOperation { operation ->
        val target = ActivationInput.port(port)
        val pairingCode = ActivationInput.pairingCode(code)
        update(operation, ActivationStage.Pairing, "正在与本机无线调试配对")
        val client = AdbPairingClient("127.0.0.1", target, pairingCode, key)
        gate.register(operation, client::interrupt)
        client.use {
            coroutineContext.ensureActive()
            check(it.start()) { "配对未完成，请检查配对端口与六位配对码" }
        }
        update(operation, ActivationStage.Paired, "配对成功；请使用连接端口激活服务")
    }

    fun activate(port: String) = startOperation { operation ->
        val target = ActivationInput.port(port)
        update(operation, ActivationStage.Connecting, "正在连接本机无线调试")
        val client = AdbClient("127.0.0.1", target, key)
        gate.register(operation, client::interrupt)
        client.use {
            coroutineContext.ensureActive()
            it.connect()
            update(operation, ActivationStage.Starting, "正在启动内置 Shell 服务")
            val log = StringBuilder()
            it.shellCommand(command()) { bytes ->
                if (log.length < 32_768) log.append(bytes.toString(Charsets.UTF_8).take(32_768 - log.length))
                if (gate.isCurrent(operation)) mutableStatus.value = mutableStatus.value.copy(log = log.toString())
            }
        }
        update(operation, ActivationStage.WaitingBinder, "启动命令已执行，等待验证内置服务")
        repository.state.first { it is ServiceState.Connected }
        update(operation, ActivationStage.Connected, "内置服务已连接（Shell UID 2000）")
    }

    private fun startOperation(block: suspend (Long) -> Unit) {
        val operation = gate.begin() ?: return
        job = scope.launch(Dispatchers.IO) {
            val watchdog = scope.launch(Dispatchers.Default) {
                delay(45_000)
                gate.interrupt(operation)
            }
            try {
                withTimeout(45_000) { block(operation) }
            } catch (_: TimeoutCancellationException) {
                update(operation, ActivationStage.Failed, "操作超时，请检查无线调试是否开启、端口是否正确")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update(operation, ActivationStage.Failed, error.message ?: "无线调试操作失败")
            } finally {
                watchdog.cancel()
                gate.finish(operation)
            }
        }
    }

    private fun update(operation: Long, stage: ActivationStage, message: String) {
        if (gate.isCurrent(operation)) mutableStatus.value = mutableStatus.value.copy(stage = stage, message = message)
    }

    fun cancel() {
        gate.cancel()
        job?.cancel()
        mutableStatus.value = ActivationStatus(ActivationStage.Canceled, "操作已取消")
    }
}

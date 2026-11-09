package com.chichu.toolbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import com.chichu.toolbox.core.shizuku.activation.ActivationStage
import com.chichu.toolbox.core.shizuku.adb.AdbDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** User-started pairing notification; no boot receiver, automatic activation or intent redelivery. */
class PairingNotificationService : Service() {
    companion object {
        private const val CHANNEL = "wireless_pairing"
        private const val NOTIFICATION = 101
        private const val RESULT_NOTIFICATION = 102
        private const val START = "com.chichu.toolbox.pairing.START"
        private const val STOP = "com.chichu.toolbox.pairing.STOP"
        private const val REPLY = "com.chichu.toolbox.pairing.REPLY"
        private const val CODE = "pairing_code"
        private const val PORT = "port"
        private const val SESSION = "session"

        fun prepare(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "无线调试配对", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "在系统无线调试页面通过通知输入配对码"
                    setSound(null, null)
                    setShowBadge(false)
                    setAllowBubbles(false)
                },
            )
        }

        fun notificationsEnabled(context: Context): Boolean {
            prepare(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
        }

        fun start(context: Context) { context.startForegroundService(Intent(context, PairingNotificationService::class.java).setAction(START)) }
        fun stop(context: Context) { context.stopService(Intent(context, PairingNotificationService::class.java)) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as ToolboxApplication).container
    private val manager get() = getSystemService(NotificationManager::class.java)
    private var discovery: Job? = null
    private var expiry: Job? = null
    private var pairing: Job? = null
    private var replyIntent: PendingIntent? = null
    private var port: Int? = null
    private var endpoints = emptyList<Int>()
    private var session = 0L
    private var started = false
    private var working = false
    private var finished = false

    override fun onCreate() { super.onCreate(); prepare(this) }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> {
                if (!started) {
                    started = true
                    manager.cancel(RESULT_NOTIFICATION)
                    session = SystemClock.elapsedRealtimeNanos()
                    startForeground(NOTIFICATION, notification("正在搜索配对服务", "在系统无线调试中打开「使用配对码配对设备」。"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                    search()
                }
            }
            STOP -> { finish(null, null) }
            REPLY -> {
                // Only accept replies for the currently discovered, local endpoint in this process.
                // The code stays in memory and is never copied to notification history or logs.
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CODE)?.toString()?.trim().orEmpty()
                val target = intent.getIntExtra(PORT, -1)
                if (started && !finished && !working && intent.getLongExtra(SESSION, -1) == session && target == port) {
                    if (code.length != 6 || code.any { !it.isDigit() || it !in '0'..'9' }) {
                        publish("配对码应为六位数字", "请查看系统配对窗口，并重新输入。", target)
                    } else pair(target, code)
                } else if (!started) stopSelf()
            }
            else -> { if (!started) stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun search() {
        expiry = scope.launch {
            delay(180_000)
            finish("配对搜索已结束", "搜索已超时，请返回工具箱重试。")
        }
        discovery = scope.launch {
            try {
                AdbDiscovery(this@PairingNotificationService).discover(true).collect { endpoints ->
                    this@PairingNotificationService.endpoints = endpoints.map { it.port }
                    if (!working && !finished) publishEndpoint()
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { finish("无法搜索配对服务", "请返回工具箱检查本地网络权限，或使用手动配对。") }
        }
    }

    private fun publishEndpoint(force: Boolean = false) {
        val next = port?.takeIf { it in endpoints } ?: endpoints.firstOrNull()
        if (next == port && !force) return
        port = next
        if (next == null) publish("正在搜索配对服务", "请保持系统配对码窗口打开。")
        else publish("已发现无线调试配对服务", "点「输入配对码」，填写系统窗口中的六位数字。", next)
    }

    private fun pair(target: Int, code: String) {
        val operation = container.activation.pair(target.toString(), code)
        if (operation == null) {
            publish("另一个激活操作正在进行", "请等待当前操作结束后重试。", target)
            return
        }
        pairing = operation
        working = true
        publish("正在配对", "正在验证本机无线调试配对信息。")
        scope.launch {
            operation.join()
            working = false
            if (finished) return@launch
            when (container.activation.status.value.stage) {
                ActivationStage.Paired -> finish("配对成功", "返回工具箱，使用连接端口启动内置服务。")
                ActivationStage.Canceled -> finish("配对已取消", "可返回工具箱重新开始。")
                else -> {
                    port = port?.takeIf { it in endpoints } ?: endpoints.firstOrNull()
                    if (port == null) publishEndpoint(force = true)
                    else publish("配对未完成", "请确认配对码窗口仍打开，并重新输入当前配对码。", port)
                }
            }
        }
    }

    private fun publish(title: String, text: String, target: Int? = null) {
        replyIntent?.cancel()
        replyIntent = null
        manager.notify(NOTIFICATION, notification(title, text, target))
    }

    private fun notification(title: String, text: String, target: Int? = null, ongoing: Boolean = true): Notification {
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(ongoing).setAutoCancel(!ongoing).setOnlyAlertOnce(target == null)
            .setAllowSystemGeneratedContextualActions(false)
            .apply {
                if (target != null) {
                    val reply = PendingIntent.getForegroundService(
                        this@PairingNotificationService, 1,
                        Intent(this@PairingNotificationService, PairingNotificationService::class.java).setAction(REPLY)
                            .setIdentifier("$session:$target").putExtra(PORT, target).putExtra(SESSION, session),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    replyIntent = reply
                    addAction(Notification.Action.Builder(null, "输入配对码", reply)
                        .addRemoteInput(RemoteInput.Builder(CODE).setLabel("六位配对码").setAllowFreeFormInput(true).build())
                        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).setAllowGeneratedReplies(false).build())
                }
                if (ongoing) {
                    val stop = PendingIntent.getService(this@PairingNotificationService, 2, Intent(this@PairingNotificationService, PairingNotificationService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
                    addAction(Notification.Action.Builder(null, if (working) "取消配对" else "停止搜索", stop).build())
                }
            }.build()
    }

    private fun finish(title: String?, text: String?) {
        if (finished) return
        finished = true
        discovery?.cancel(); expiry?.cancel(); replyIntent?.cancel(); replyIntent = null
        if (pairing?.isActive == true) container.activation.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (title != null) manager.notify(RESULT_NOTIFICATION, notification(title, text.orEmpty(), ongoing = false))
        stopSelf()
    }

    override fun onDestroy() {
        if (!finished) {
            finished = true
            if (pairing?.isActive == true) container.activation.cancel()
            manager.cancel(NOTIFICATION)
        }
        replyIntent?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}

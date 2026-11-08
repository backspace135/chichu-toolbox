package com.chichu.toolbox.core.shizuku.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicLong

data class AdbEndpoint(val name: String, val port: Int, val pairing: Boolean)

class AdbDiscovery(context: Context) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val sessions = AtomicLong()
    private data class Resolution(val key: String, val info: NsdServiceInfo, val deliver: (NsdServiceInfo) -> Unit)
    @Suppress("DEPRECATION")
    private val resolver = SerialResolver<Resolution>({ request, done ->
        manager.resolveService(request.info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { done(null) }
            override fun onServiceResolved(info: NsdServiceInfo) { done(request.copy(info = info)) }
        })
    }, { it.deliver(it.info) })

    @Suppress("DEPRECATION")
    fun discover(pairing: Boolean) = callbackFlow {
        val endpoints = mutableMapOf<String, AdbEndpoint>()
        val session = sessions.incrementAndGet()
        val found = mutableSetOf<String>()
        fun key(service: NsdServiceInfo) = "$session:${service.serviceName}"
        val localAddresses = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.toSet()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { close(IllegalStateException("发现服务失败（$code），可手动输入端口")) }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceLost(service: NsdServiceInfo) {
                resolver.lost(key(service))
                synchronized(endpoints) { found.remove(key(service)); endpoints.remove(service.serviceName); trySend(endpoints.values.toList()) }
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                synchronized(endpoints) { found.add(key(service)) }
                resolver.found(key(service), Resolution(key(service), service) { info ->
                        if (info.host !in localAddresses && info.host?.isLoopbackAddress != true) return@Resolution
                        synchronized(endpoints) {
                            endpoints[info.serviceName] = AdbEndpoint(info.serviceName, info.port, pairing)
                            trySend(endpoints.values.toList())
                        }
                })
            }
        }
        manager.discoverServices(if (pairing) "_adb-tls-pairing._tcp." else "_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            val keys = synchronized(endpoints) { found.toList().also { found.clear() } }
            keys.forEach(resolver::lost)
            runCatching { manager.stopServiceDiscovery(listener) }
        }
    }
}

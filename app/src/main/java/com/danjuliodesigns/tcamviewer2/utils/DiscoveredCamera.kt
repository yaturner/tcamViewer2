package com.danjuliodesigns.tcamviewer2.utils

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.danjuliodesigns.tcamviewer2.constants.Constants
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/** One tCam camera found via mDNS: its advertised service name and resolved IPv4 address. */
data class DiscoveredCamera(val name: String, val ip: String)

/** Scans the network via mDNS (_tcam-socket._tcp.) for tCam devices, resolving each match's IP.
 *  Shared by the manual "Find cameras" dialog in Settings and by auto-reconnect, which needs to
 *  find a camera's new address after its DHCP lease changes. */
suspend fun discoverTcamCameras(
    context: Context,
    timeoutMs: Long = 10_000L,
): List<DiscoveredCamera> {
    val nsdManager = context.getSystemService(NsdManager::class.java) ?: return emptyList()
    val found = mutableListOf<DiscoveredCamera>()
    val pendingResolves = Channel<NsdServiceInfo>(Channel.UNLIMITED)

    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceName.lowercase().startsWith("tcam")) {
                pendingResolves.trySend(serviceInfo)
            }
        }
    }

    // NsdManager is supposed to handle multicast reception on the app's behalf, but on some
    // devices/Android versions mDNS packets never reach the socket unless the app holds its own
    // multicast lock — a long-standing platform quirk, not something NsdManager reliably covers.
    val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    val multicastLock = wifiManager?.createMulticastLock("tcamDiscovery")?.apply {
        setReferenceCounted(true)
        acquire()
    }

    nsdManager.discoverServices(Constants.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    try {
        withTimeoutOrNull(timeoutMs) {
            for (serviceInfo in pendingResolves) {
                val resolved = suspendCancellableCoroutine { cont ->
                    nsdManager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                cont.resume(null)
                            }

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                cont.resume(info)
                            }
                        },
                    )
                }
                resolved?.let { info ->
                    val ip = (info.host as? Inet4Address)?.hostAddress ?: return@let
                    val name = info.serviceName
                    if (found.none { it.name == name }) found.add(DiscoveredCamera(name, ip))
                }
            }
        }
    } finally {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (_: Exception) {
        }
        try {
            if (multicastLock?.isHeld == true) multicastLock.release()
        } catch (_: Exception) {
        }
    }
    return found
}

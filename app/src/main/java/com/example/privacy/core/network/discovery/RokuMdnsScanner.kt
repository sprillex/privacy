package com.example.privacy.core.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.privacy.core.network.ecp.RokuEcpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RokuMdnsScanner(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(500, TimeUnit.MILLISECONDS)
        .build()
) {
    companion object {
        val SERVICE_TYPES = listOf("_airplay._tcp.", "_display._tcp.")
    }

    fun startDiscovery(): Flow<DiscoveredDevice> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsdManager == null) {
            close()
            return@callbackFlow
        }

        val discoveredIps = mutableSetOf<String>()
        val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

        fun probeAndEmit(ip: String) {
            if (!discoveredIps.add(ip)) return
            launch(Dispatchers.IO) {
                val device = probeIp(ip)
                if (device != null) {
                    trySend(device)
                }
            }
        }

        for (serviceType in SERVICE_TYPES) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}

                override fun onServiceFound(service: NsdServiceInfo) {
                    try {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val hostIp = serviceInfo.host?.hostAddress
                                if (!hostIp.isNullOrBlank()) {
                                    probeAndEmit(hostIp)
                                }
                            }
                        })
                    } catch (_: Exception) {}
                }

                override fun onServiceLost(service: NsdServiceInfo) {}

                override fun onDiscoveryStopped(serviceType: String) {}

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try {
                        nsdManager.stopServiceDiscovery(this)
                    } catch (_: Exception) {}
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }

            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                discoveryListeners.add(listener)
            } catch (_: Exception) {}
        }

        awaitClose {
            for (listener in discoveryListeners) {
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (_: Exception) {}
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun probeIp(ip: String): DiscoveredDevice? {
        val url = "http://$ip:8060/query/device-info"
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val xmlContent = response.body?.string() ?: ""
                    val info = RokuEcpClient.parseDeviceInfoXml(xmlContent)
                    val usn = when {
                        info.udn.isNotBlank() -> info.udn
                        info.serialNumber.isNotBlank() -> "uuid:roku:ecp:${info.serialNumber}"
                        else -> "usn:$ip"
                    }
                    DiscoveredDevice(
                        usn = usn,
                        location = "http://$ip:8060/",
                        ipAddress = ip,
                        port = 8060
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

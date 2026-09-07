package com.example.privacy.core.network.discovery

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket

data class DiscoveredDevice(
    val usn: String,
    val location: String,
    val ipAddress: String,
    val port: Int
)

class RokuSsdpScanner(
    private val context: Context,
    private val scanTimeoutMs: Long = 10_000L
) {
    companion object {
        const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val MSEARCH_ST = "roku:ecp"

        const val MSEARCH_REQUEST =
            "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: roku:ecp\r\n" +
                    "\r\n"

        fun parseSsdpResponse(response: String): DiscoveredDevice? {
            val lines = response.lines()
            var location: String? = null
            var usn: String? = null

            for (line in lines) {
                val trimmed = line.trim()
                val lowerCaseLine = trimmed.lowercase()
                when {
                    lowerCaseLine.startsWith("location:") -> {
                        location = trimmed.substring("location:".length).trim()
                    }
                    lowerCaseLine.startsWith("usn:") -> {
                        usn = trimmed.substring("usn:".length).trim()
                    }
                }
            }

            if (location == null) return null

            // Parse IP and port from LOCATION header (e.g. http://192.168.1.120:8060/)
            val cleanLocation = location.trim()
            val uri = try {
                java.net.URI(cleanLocation)
            } catch (e: Exception) {
                return null
            }

            val ipAddress = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else 8060
            val effectiveUsn = usn ?: "usn:$ipAddress"

            return DiscoveredDevice(
                usn = effectiveUsn,
                location = cleanLocation,
                ipAddress = ipAddress,
                port = port
            )
        }
    }

    fun startDiscovery(): Flow<DiscoveredDevice> = flow {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("RokuSsdpScannerLock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        val discoveredUsns = mutableSetOf<String>()
        var socket: DatagramSocket? = null

        try {
            val socketImpl = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
                soTimeout = 1000
            }
            socket = socketImpl

            val ssdpGroup = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
            val requestBytes = MSEARCH_REQUEST.toByteArray(Charsets.UTF_8)
            val sendPacket = DatagramPacket(requestBytes, requestBytes.size, ssdpGroup, SSDP_PORT)

            // Send initial probe
            socketImpl.send(sendPacket)

            val startTime = System.currentTimeMillis()
            val buffer = ByteArray(2048)

            while (currentCoroutineContext().isActive && (System.currentTimeMillis() - startTime) < scanTimeoutMs) {
                try {
                    val receivePacket = DatagramPacket(buffer, buffer.size)
                    socketImpl.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                    val device = parseSsdpResponse(response)

                    if (device != null && discoveredUsns.add(device.usn)) {
                        emit(device)
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Send periodic re-probe if still within scan window
                    if ((System.currentTimeMillis() - startTime) < scanTimeoutMs) {
                        try {
                            socketImpl.send(sendPacket)
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    // Socket closed or error
                    break
                }
            }
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)
}

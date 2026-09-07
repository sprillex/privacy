package com.example.privacy.core.network.discovery

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

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

        const val MSEARCH_ROKU_ST =
            "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: roku:ecp\r\n" +
                    "\r\n"

        const val MSEARCH_DIAL_ST =
            "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: urn:dial-multiscreen-org:service:dial:1\r\n" +
                    "\r\n"

        const val MSEARCH_ALL_ST =
            "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: ssdp:all\r\n" +
                    "\r\n"

        fun parseSsdpResponse(response: String): DiscoveredDevice? {
            val lines = response.lines()
            var location: String? = null
            var usn: String? = null
            var isRoku = false

            for (line in lines) {
                val trimmed = line.trim()
                val lowerCaseLine = trimmed.lowercase()

                if (lowerCaseLine.startsWith("location:")) {
                    location = trimmed.substring("location:".length).trim()
                }
                if (lowerCaseLine.startsWith("usn:")) {
                    usn = trimmed.substring("usn:".length).trim()
                }
                if (lowerCaseLine.startsWith("st:")) {
                    val st = trimmed.substring("st:".length).trim().lowercase()
                    if (st.contains("roku") || st.contains("dial")) {
                        isRoku = true
                    }
                }
                if (lowerCaseLine.contains("roku")) {
                    isRoku = true
                }
            }

            if (location == null) return null

            val uri = try {
                java.net.URI(location.trim())
            } catch (e: Exception) {
                return null
            }

            val ipAddress = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else 8060

            val effectiveUsn = usn ?: "usn:$ipAddress"
            if (!isRoku && !effectiveUsn.lowercase().contains("roku") && !location.lowercase().contains("8060") && uri.port != 8060) {
                return null
            }

            return DiscoveredDevice(
                usn = effectiveUsn,
                location = location.trim(),
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
            val packetRoku = MSEARCH_ROKU_ST.toByteArray(Charsets.UTF_8)
            val packetDial = MSEARCH_DIAL_ST.toByteArray(Charsets.UTF_8)
            val packetAll = MSEARCH_ALL_ST.toByteArray(Charsets.UTF_8)

            val sendPacketRoku = DatagramPacket(packetRoku, packetRoku.size, ssdpGroup, SSDP_PORT)
            val sendPacketDial = DatagramPacket(packetDial, packetDial.size, ssdpGroup, SSDP_PORT)
            val sendPacketAll = DatagramPacket(packetAll, packetAll.size, ssdpGroup, SSDP_PORT)

            // Send initial probes
            socketImpl.send(sendPacketRoku)
            socketImpl.send(sendPacketDial)
            socketImpl.send(sendPacketAll)

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
                    if ((System.currentTimeMillis() - startTime) < scanTimeoutMs) {
                        try {
                            socketImpl.send(sendPacketRoku)
                            socketImpl.send(sendPacketDial)
                            socketImpl.send(sendPacketAll)
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
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

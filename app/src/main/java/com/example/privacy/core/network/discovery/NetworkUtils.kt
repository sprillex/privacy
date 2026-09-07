package com.example.privacy.core.network.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.NetworkInterface

object NetworkUtils {
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return false
            }
        }
        val ip = getLocalWifiIpAddress(context)
        return !ip.isNullOrEmpty() && ip != "0.0.0.0" && ip != "127.0.0.1"
    }

    fun getLocalWifiIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifiManager?.dhcpInfo
            if (dhcpInfo != null && dhcpInfo.ipAddress != 0) {
                val ip = dhcpInfo.ipAddress
                val formattedIp = String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
                if (formattedIp != "0.0.0.0") return formattedIp
            }

            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (netInterface in interfaces.toList()) {
                if (!netInterface.isUp || netInterface.isLoopback) continue
                val interfaceName = netInterface.name.lowercase()
                if (interfaceName.contains("wlan") || interfaceName.contains("eth") || interfaceName.contains("ap") || interfaceName.contains("swlan")) {
                    for (inetAddress in netInterface.inetAddresses.toList()) {
                        if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                            val hostAddress = inetAddress.hostAddress
                            if (!hostAddress.isNullOrEmpty() && hostAddress != "0.0.0.0") {
                                return hostAddress
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getSubnetPrefix(ipAddress: String): String? {
        val parts = ipAddress.split(".")
        if (parts.size == 4) {
            return "${parts[0]}.${parts[1]}.${parts[2]}"
        }
        return null
    }
}

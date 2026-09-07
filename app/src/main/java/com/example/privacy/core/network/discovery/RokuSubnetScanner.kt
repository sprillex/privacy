package com.example.privacy.core.network.discovery

import com.example.privacy.core.network.ecp.RokuEcpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RokuSubnetScanner(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(250, TimeUnit.MILLISECONDS)
        .readTimeout(300, TimeUnit.MILLISECONDS)
        .writeTimeout(300, TimeUnit.MILLISECONDS)
        .callTimeout(600, TimeUnit.MILLISECONDS)
        .build()
) {
    fun scanSubnet(subnetPrefix: String): Flow<DiscoveredDevice> = flow {
        coroutineScope {
            val jobs = (1..254).map { host ->
                val ip = "$subnetPrefix.$host"
                async(Dispatchers.IO) {
                    probeIp(ip)
                }
            }
            jobs.forEach { job ->
                val device = job.await()
                if (device != null) {
                    emit(device)
                }
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

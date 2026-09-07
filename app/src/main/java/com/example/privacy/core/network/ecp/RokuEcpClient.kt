package com.example.privacy.core.network.ecp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class RokuDeviceInfo(
    val friendlyDeviceName: String,
    val modelName: String,
    val modelNumber: String,
    val isTv: Boolean,
    val softwareVersion: String,
    val serialNumber: String = "",
    val udn: String = ""
)

class RokuEcpClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2000, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .writeTimeout(2000, TimeUnit.MILLISECONDS)
        .build()
) {
    companion object {
        fun parseDeviceInfoXml(xmlContent: String): RokuDeviceInfo {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val inputStream = ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8))
            val document = builder.parse(inputStream)
            document.documentElement.normalize()

            fun getTagValue(tagName: String): String {
                val nodeList = document.getElementsByTagName(tagName)
                if (nodeList.length > 0) {
                    val node = nodeList.item(0)
                    if (node is Element) {
                        return node.textContent?.trim().orEmpty()
                    }
                }
                return ""
            }

            val friendlyDeviceName = getTagValue("friendly-device-name")
            val userDeviceName = getTagValue("user-device-name")
            val modelName = getTagValue("model-name")
            val modelNumber = getTagValue("model-number")
            val isTvStr = getTagValue("is-tv")
            val softwareVersion = getTagValue("software-version")
            val serialNumber = getTagValue("serial-number")
            val udn = getTagValue("udn")

            val isTv = isTvStr.equals("true", ignoreCase = true)
            val finalName = userDeviceName.ifBlank { friendlyDeviceName.ifBlank { "Roku Device" } }

            return RokuDeviceInfo(
                friendlyDeviceName = finalName,
                modelName = modelName,
                modelNumber = modelNumber,
                isTv = isTv,
                softwareVersion = softwareVersion,
                serialNumber = serialNumber,
                udn = udn
            )
        }
    }

    suspend fun getDeviceInfo(ipAddress: String, port: Int = 8060): Result<RokuDeviceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "http://$ipAddress:$port/query/device-info"
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} fetching device info")
                }
                val bodyString = response.body?.string() ?: ""
                parseDeviceInfoXml(bodyString)
            }
        }
    }

    suspend fun sendKey(ipAddress: String, key: String, port: Int = 8060): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "http://$ipAddress:$port/keypress/$key"
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody(null))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }
    }
}

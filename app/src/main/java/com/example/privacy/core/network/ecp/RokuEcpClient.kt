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
    val udn: String = "",
    val ecpSettingControl: String = "permissive"
) {
    val isLimitedMode: Boolean
        get() = ecpSettingControl.equals("limited", ignoreCase = true) ||
                ecpSettingControl.equals("disabled", ignoreCase = true)
}

class RokuEcpClient(
    private val okHttpClient: OkHttpClient = createDefaultClient()
) {
    companion object {
        private fun createDefaultClient(): OkHttpClient {
            return try {
                val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                    object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    }
                )
                val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                val sslSocketFactory = sslContext.socketFactory

                OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .connectTimeout(2000, TimeUnit.MILLISECONDS)
                    .readTimeout(2000, TimeUnit.MILLISECONDS)
                    .writeTimeout(2000, TimeUnit.MILLISECONDS)
                    .build()
            } catch (_: Exception) {
                OkHttpClient.Builder()
                    .connectTimeout(2000, TimeUnit.MILLISECONDS)
                    .readTimeout(2000, TimeUnit.MILLISECONDS)
                    .writeTimeout(2000, TimeUnit.MILLISECONDS)
                    .build()
            }
        }

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
            val ecpSettingControl = getTagValue("ecp-setting-control")

            val isTv = isTvStr.equals("true", ignoreCase = true)
            val finalName = userDeviceName.ifBlank { friendlyDeviceName.ifBlank { "Roku Device" } }

            return RokuDeviceInfo(
                friendlyDeviceName = finalName,
                modelName = modelName,
                modelNumber = modelNumber,
                isTv = isTv,
                softwareVersion = softwareVersion,
                serialNumber = serialNumber,
                udn = udn,
                ecpSettingControl = ecpSettingControl.ifBlank { "permissive" }
            )
        }
    }

    suspend fun getDeviceInfo(ipAddress: String, port: Int = 8060): Result<RokuDeviceInfo> = withContext(Dispatchers.IO) {
        val cleanIp = ipAddress.removePrefix("http://").removePrefix("https://").trim().trimEnd('/')
        runCatching {
            val url = "http://$cleanIp:$port/query/device-info"
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
        val cleanIp = ipAddress.removePrefix("http://").removePrefix("https://").trim().trimEnd('/')
        runCatching {
            val url = "http://$cleanIp:$port/keypress/$key"
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody(null))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} sending key $key")
                }
                true
            }
        }
    }
}

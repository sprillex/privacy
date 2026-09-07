package com.example.privacy.feature.discovery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.privacy.core.database.PrivacyCheckItemEntity
import com.example.privacy.core.database.RokuDatabase
import com.example.privacy.core.database.RokuDeviceEntity
import com.example.privacy.core.database.RokuDeviceWithChecks
import com.example.privacy.core.network.discovery.DiscoveredDevice
import com.example.privacy.core.network.discovery.NetworkUtils
import com.example.privacy.core.network.discovery.RokuMdnsScanner
import com.example.privacy.core.network.discovery.RokuSsdpScanner
import com.example.privacy.core.network.discovery.RokuSubnetScanner
import com.example.privacy.core.network.ecp.RokuEcpClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class DiscoveryUiState(
    val isScanning: Boolean = false,
    val discoveredDevices: List<RokuDeviceWithChecks> = emptyList(),
    val errorMessage: String? = null,
    val isWifiConnected: Boolean = true
)

class DiscoveryViewModel @JvmOverloads constructor(
    application: Application,
    private val scanner: RokuSsdpScanner = RokuSsdpScanner(application),
    private val subnetScanner: RokuSubnetScanner = RokuSubnetScanner(),
    private val mdnsScanner: RokuMdnsScanner = RokuMdnsScanner(application),
    private val ecpClient: RokuEcpClient = RokuEcpClient()
) : AndroidViewModel(application) {
    private val database = RokuDatabase.getDatabase(application)
    private val dao = database.rokuDao()

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        observeSavedDevices()
    }

    private fun observeSavedDevices() {
        viewModelScope.launch {
            dao.getAllDevicesWithChecks().collectLatest { devices ->
                _uiState.value = _uiState.value.copy(discoveredDevices = devices)
            }
        }
    }

    fun startScan() {
        if (_uiState.value.isScanning) return

        val wifiConnected = NetworkUtils.isWifiConnected(getApplication())
        if (!wifiConnected) {
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                isWifiConnected = false
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isScanning = true,
            isWifiConnected = true,
            errorMessage = null
        )

        val processedSet = ConcurrentHashMap.newKeySet<String>()

        viewModelScope.launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    coroutineScope {
                        launch {
                            try {
                                scanner.startDiscovery().collect { discovered ->
                                    handleDiscoveredDevice(discovered, processedSet)
                                }
                            } catch (_: Exception) {}
                        }

                        launch {
                            try {
                                mdnsScanner.startDiscovery().collect { discovered ->
                                    handleDiscoveredDevice(discovered, processedSet)
                                }
                            } catch (_: Exception) {}
                        }

                        val localIp = NetworkUtils.getLocalWifiIpAddress(getApplication())
                        val prefix = localIp?.let { NetworkUtils.getSubnetPrefix(it) }
                        if (!prefix.isNullOrBlank()) {
                            launch {
                                try {
                                    subnetScanner.scanSubnet(prefix).collect { discovered ->
                                        handleDiscoveredDevice(discovered, processedSet)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Scan error: ${e.localizedMessage}")
            } finally {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    private suspend fun handleDiscoveredDevice(
        discovered: DiscoveredDevice,
        processedSet: MutableSet<String>
    ) {
        val addedIp = processedSet.add(discovered.ipAddress)
        val addedUsn = processedSet.add(discovered.usn)
        if (!addedIp && !addedUsn) {
            return
        }

        val infoResult = ecpClient.getDeviceInfo(discovered.ipAddress, discovered.port)
        if (infoResult.isSuccess) {
            val info = infoResult.getOrThrow()
            val deviceId = if (discovered.usn.isNotBlank()) discovered.usn else "usn:${discovered.ipAddress}"
            val deviceEntity = RokuDeviceEntity(
                deviceId = deviceId,
                ipAddress = discovered.ipAddress,
                port = discovered.port,
                name = info.friendlyDeviceName,
                modelName = info.modelName,
                modelNumber = info.modelNumber,
                isTv = info.isTv,
                softwareVersion = info.softwareVersion
            )
            dao.insertOrUpdateDevice(deviceEntity)

            val defaultChecks = mutableListOf(
                PrivacyCheckItemEntity(deviceId = deviceId, targetKey = "AD_TRACKING", isVerified = false)
            )
            if (info.isTv) {
                defaultChecks.add(
                    PrivacyCheckItemEntity(deviceId = deviceId, targetKey = "ACR", isVerified = false)
                )
            }
            dao.insertCheckItems(defaultChecks)
        }
    }

    fun addDeviceByIp(ipAddress: String, onSuccess: (String) -> Unit) {
        val trimmedIp = ipAddress.trim()
        if (trimmedIp.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)
            try {
                val result = ecpClient.getDeviceInfo(trimmedIp, 8060)
                if (result.isSuccess) {
                    val info = result.getOrThrow()
                    val deviceId = when {
                        info.udn.isNotBlank() -> info.udn
                        info.serialNumber.isNotBlank() -> "uuid:roku:ecp:${info.serialNumber}"
                        else -> "usn:$trimmedIp"
                    }
                    val deviceEntity = RokuDeviceEntity(
                        deviceId = deviceId,
                        ipAddress = trimmedIp,
                        port = 8060,
                        name = info.friendlyDeviceName,
                        modelName = info.modelName,
                        modelNumber = info.modelNumber,
                        isTv = info.isTv,
                        softwareVersion = info.softwareVersion
                    )
                    dao.insertOrUpdateDevice(deviceEntity)

                    val defaultChecks = mutableListOf(
                        PrivacyCheckItemEntity(deviceId = deviceId, targetKey = "AD_TRACKING", isVerified = false)
                    )
                    if (info.isTv) {
                        defaultChecks.add(
                            PrivacyCheckItemEntity(deviceId = deviceId, targetKey = "ACR", isVerified = false)
                        )
                    }
                    dao.insertCheckItems(defaultChecks)

                    onSuccess(deviceId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Could not find Roku device at $trimmedIp. Please check the IP address and network connection."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error connecting to $trimmedIp: ${e.localizedMessage}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

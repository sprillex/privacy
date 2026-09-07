package com.example.privacy.feature.discovery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.privacy.core.database.PrivacyCheckItemEntity
import com.example.privacy.core.database.RokuDatabase
import com.example.privacy.core.database.RokuDeviceEntity
import com.example.privacy.core.database.RokuDeviceWithChecks
import com.example.privacy.core.network.discovery.RokuSsdpScanner
import com.example.privacy.core.network.ecp.RokuEcpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class DiscoveryUiState(
    val isScanning: Boolean = false,
    val discoveredDevices: List<RokuDeviceWithChecks> = emptyList(),
    val errorMessage: String? = null
)

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RokuDatabase.getDatabase(application)
    private val dao = database.rokuDao()
    private val scanner = RokuSsdpScanner(application)
    private val ecpClient = RokuEcpClient()

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
        _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)

        viewModelScope.launch {
            try {
                scanner.startDiscovery().collect { discovered ->
                    // Query ECP device info (now main-safe with Dispatchers.IO)
                    val infoResult = ecpClient.getDeviceInfo(discovered.ipAddress, discovered.port)
                    if (infoResult.isSuccess) {
                        val info = infoResult.getOrThrow()
                        val deviceId = discovered.usn
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

                        // Insert initial privacy check items if not exists
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Scan error: ${e.localizedMessage}")
            } finally {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

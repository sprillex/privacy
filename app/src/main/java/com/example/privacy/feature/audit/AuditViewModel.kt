package com.example.privacy.feature.audit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.privacy.core.database.PrivacyCheckItemEntity
import com.example.privacy.core.database.RokuDatabase
import com.example.privacy.core.database.RokuDeviceWithChecks
import com.example.privacy.core.network.ecp.RokuEcpClient
import com.example.privacy.feature.privacy.NavigationProgressState
import com.example.privacy.feature.privacy.RokuPrivacyNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AuditUiState(
    val deviceWithChecks: RokuDeviceWithChecks? = null,
    val macroState: NavigationProgressState = NavigationProgressState.Idle,
    val errorMessage: String? = null,
    val isLimitedMode: Boolean = false,
    val isCheckingLimitedMode: Boolean = false,
    val hasCheckedLimitedMode: Boolean = false
)

class AuditViewModel @JvmOverloads constructor(
    application: Application,
    private val deviceId: String,
    private val ecpClient: RokuEcpClient = RokuEcpClient(),
    private val navigator: RokuPrivacyNavigator = RokuPrivacyNavigator(ecpClient)
) : AndroidViewModel(application) {

    private val database = RokuDatabase.getDatabase(application)
    private val dao = database.rokuDao()

    private val _uiState = MutableStateFlow(AuditUiState())
    val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

    init {
        observeDevice()
    }

    private fun observeDevice() {
        viewModelScope.launch {
            dao.getDeviceWithChecks(deviceId).collectLatest { deviceWithChecks ->
                _uiState.value = _uiState.value.copy(deviceWithChecks = deviceWithChecks)
                if (deviceWithChecks != null && !_uiState.value.hasCheckedLimitedMode) {
                    checkLimitedMode()
                }
            }
        }
    }

    fun checkLimitedMode() {
        val device = _uiState.value.deviceWithChecks?.device ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingLimitedMode = true)
            val infoResult = ecpClient.getDeviceInfo(device.ipAddress, device.port)
            if (infoResult.isSuccess) {
                val info = infoResult.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    isLimitedMode = info.isLimitedMode,
                    isCheckingLimitedMode = false,
                    hasCheckedLimitedMode = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isCheckingLimitedMode = false,
                    hasCheckedLimitedMode = true
                )
            }
        }
    }

    fun triggerAdTrackingMacro() {
        val device = _uiState.value.deviceWithChecks?.device ?: return
        viewModelScope.launch {
            navigator.executeAdTrackingMacro(device.ipAddress, device.port).collect { state ->
                _uiState.value = _uiState.value.copy(macroState = state)
                if (state is NavigationProgressState.Error) {
                    verifyLimitedModeOnError()
                }
            }
        }
    }

    fun triggerAcrMacro() {
        val device = _uiState.value.deviceWithChecks?.device ?: return
        viewModelScope.launch {
            navigator.executeAcrMacro(device.ipAddress, device.isTv, device.port).collect { state ->
                _uiState.value = _uiState.value.copy(macroState = state)
                if (state is NavigationProgressState.Error) {
                    verifyLimitedModeOnError()
                }
            }
        }
    }

    fun toggleCheckItemVerification(checkItem: PrivacyCheckItemEntity, isChecked: Boolean) {
        viewModelScope.launch {
            val updated = checkItem.copy(
                isVerified = isChecked,
                verifiedTimestamp = if (isChecked) System.currentTimeMillis() else null
            )
            dao.updateCheckItem(updated)
            dao.updateLastAuditedAt(deviceId, System.currentTimeMillis())
        }
    }

    fun sendRemoteKey(key: String) {
        val device = _uiState.value.deviceWithChecks?.device ?: return
        viewModelScope.launch {
            val result = ecpClient.sendKey(device.ipAddress, key, device.port)
            if (result.isFailure) {
                verifyLimitedModeOnError()
            }
        }
    }

    private fun verifyLimitedModeOnError() {
        val device = _uiState.value.deviceWithChecks?.device ?: return
        viewModelScope.launch {
            val infoResult = ecpClient.getDeviceInfo(device.ipAddress, device.port)
            if (infoResult.isSuccess && infoResult.getOrThrow().isLimitedMode) {
                _uiState.value = _uiState.value.copy(
                    isLimitedMode = true,
                    errorMessage = "TV is set to Limited Control mode."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to communicate with TV."
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

package com.example.privacy.feature.privacy

import com.example.privacy.core.network.ecp.RokuEcpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class NavigationProgressState {
    data object Idle : NavigationProgressState()
    data class Running(val macroName: String, val stepName: String, val currentStep: Int, val totalSteps: Int) : NavigationProgressState()
    data class Completed(val macroName: String) : NavigationProgressState()
    data class Error(val macroName: String, val message: String) : NavigationProgressState()
}

class RokuPrivacyNavigator(
    private val ecpClient: RokuEcpClient
) {
    fun executeAdTrackingMacro(ipAddress: String, port: Int = 8060): Flow<NavigationProgressState> = flow {
        val macroName = "Limit Ad Tracking Macro"
        val steps = listOf(
            "Sending Home key" to "Home",
            "Focusing side menu" to "Left",
            "Navigating to Settings" to "Up",
            "Entering Settings" to "Right",
            "Navigating to Privacy" to "Up",
            "Scrolling to Privacy section" to "Up",
            "Entering Privacy" to "Right",
            "Entering Advertising" to "Right",
            "Navigating to Reset Advertising ID" to "Down",
            "Selecting Reset Advertising ID" to "Select",
            "Confirming Reset Advertising ID" to "Select"
        )
        val totalSteps = steps.size

        emit(NavigationProgressState.Running(macroName, "Starting macro...", 0, totalSteps))

        for ((index, stepInfo) in steps.withIndex()) {
            val (stepName, key) = stepInfo
            val stepNumber = index + 1
            emit(NavigationProgressState.Running(macroName, stepName, stepNumber, totalSteps))

            val result = ecpClient.sendKey(ipAddress, key, port)
            if (result.isFailure || result.getOrDefault(false) == false) {
                emit(NavigationProgressState.Error(macroName, "Failed to send key $key at step: $stepName"))
                return@flow
            }

            if (key == "Home") {
                delay(2000)
            } else {
                delay(500)
            }
        }

        emit(NavigationProgressState.Completed(macroName))
    }

    fun executeAcrMacro(ipAddress: String, isTv: Boolean, port: Int = 8060): Flow<NavigationProgressState> = flow {
        val macroName = "Disable ACR / Smart TV Experience Macro"
        if (!isTv) {
            emit(NavigationProgressState.Error(macroName, "ACR Macro is only available for Roku TV devices"))
            return@flow
        }

        val steps = listOf(
            "Sending Home key" to "Home",
            "Focusing side menu" to "Left",
            "Navigating to Settings" to "Up",
            "Entering Settings" to "Right",
            "Navigating to Privacy" to "Up",
            "Scrolling to Privacy section" to "Up",
            "Entering Privacy" to "Right",
            "Scrolling down to Smart TV experience" to "Down",
            "Selecting Smart TV experience option" to "Down",
            "Entering Smart TV experience" to "Right",
            "Selecting ACR setting" to "Select",
            "Navigating down" to "Down",
            "Confirming ACR setting" to "Select"
        )
        val totalSteps = steps.size

        emit(NavigationProgressState.Running(macroName, "Starting macro...", 0, totalSteps))

        for ((index, stepInfo) in steps.withIndex()) {
            val (stepName, key) = stepInfo
            val stepNumber = index + 1
            emit(NavigationProgressState.Running(macroName, stepName, stepNumber, totalSteps))

            val result = ecpClient.sendKey(ipAddress, key, port)
            if (result.isFailure || result.getOrDefault(false) == false) {
                emit(NavigationProgressState.Error(macroName, "Failed to send key $key at step: $stepName"))
                return@flow
            }

            if (key == "Home") {
                delay(2000)
            } else {
                delay(500)
            }
        }

        emit(NavigationProgressState.Completed(macroName))
    }
}

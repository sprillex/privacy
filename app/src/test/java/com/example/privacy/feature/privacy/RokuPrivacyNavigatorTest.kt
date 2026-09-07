package com.example.privacy.feature.privacy

import com.example.privacy.core.network.ecp.RokuEcpClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RokuPrivacyNavigatorTest {

    private val mockEcpClient = mockk<RokuEcpClient>()
    private val navigator = RokuPrivacyNavigator(mockEcpClient)

    @Test
    fun executeAdTrackingMacro_successfulExecution_emitsCompletedState() = runTest {
        coEvery { mockEcpClient.sendKey(any(), any(), any()) } returns Result.success(true)

        val states = navigator.executeAdTrackingMacro("192.168.1.100").toList()

        assertTrue(states.first() is NavigationProgressState.Running)
        assertTrue(states.last() is NavigationProgressState.Completed)
        assertEquals("Limit Ad Tracking Macro", (states.last() as NavigationProgressState.Completed).macroName)
    }

    @Test
    fun executeAdTrackingMacro_networkFailure_emitsErrorStateAndAborts() = runTest {
        coEvery { mockEcpClient.sendKey("192.168.1.100", "Home", any()) } returns Result.success(true)
        coEvery { mockEcpClient.sendKey("192.168.1.100", "Up", any()) } returns Result.failure(RuntimeException("Network error"))

        val states = navigator.executeAdTrackingMacro("192.168.1.100").toList()

        val lastState = states.last()
        assertTrue(lastState is NavigationProgressState.Error)
        val errorState = lastState as NavigationProgressState.Error
        assertEquals("Limit Ad Tracking Macro", errorState.macroName)
        assertTrue(errorState.message.contains("Failed to send key Up"))
    }

    @Test
    fun executeAcrMacro_deviceNotTv_emitsErrorState() = runTest {
        val states = navigator.executeAcrMacro("192.168.1.100", isTv = false).toList()

        val lastState = states.last()
        assertTrue(lastState is NavigationProgressState.Error)
        val errorState = lastState as NavigationProgressState.Error
        assertTrue(errorState.message.contains("only available for Roku TV"))
    }

    @Test
    fun executeAcrMacro_deviceIsTv_successfulExecution_emitsCompletedState() = runTest {
        coEvery { mockEcpClient.sendKey(any(), any(), any()) } returns Result.success(true)

        val states = navigator.executeAcrMacro("192.168.1.100", isTv = true).toList()

        assertTrue(states.last() is NavigationProgressState.Completed)
    }
}

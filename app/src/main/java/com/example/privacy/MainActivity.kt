package com.example.privacy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.privacy.feature.audit.AuditScreen
import com.example.privacy.feature.audit.AuditViewModel
import com.example.privacy.feature.discovery.DiscoveryScreen
import com.example.privacy.feature.discovery.DiscoveryViewModel

class MainActivity : ComponentActivity() {

    private val discoveryViewModel: DiscoveryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    AppNavigation(discoveryViewModel = discoveryViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(discoveryViewModel: DiscoveryViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "discovery"
    ) {
        composable("discovery") {
            DiscoveryScreen(
                viewModel = discoveryViewModel,
                onDeviceClick = { deviceId ->
                    navController.navigate("audit/${java.net.URLEncoder.encode(deviceId, "UTF-8")}")
                }
            )
        }
        composable(
            route = "audit/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedDeviceId = backStackEntry.arguments?.getString("deviceId").orEmpty()
            val deviceId = java.net.URLDecoder.decode(encodedDeviceId, "UTF-8")

            val context = androidx.compose.ui.platform.LocalContext.current
            val auditViewModel: AuditViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = deviceId,
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return AuditViewModel(
                            application = context.applicationContext as android.app.Application,
                            deviceId = deviceId
                        ) as T
                    }
                }
            )

            AuditScreen(
                viewModel = auditViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

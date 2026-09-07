package com.example.privacy.feature.discovery

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.privacy.core.database.RokuDeviceWithChecks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onDeviceClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddByIpDialog by remember { mutableStateOf(false) }
    var ipInputText by remember { mutableStateOf("") }

    val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.startScan()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    if (showAddByIpDialog) {
        AlertDialog(
            onDismissRequest = { showAddByIpDialog = false },
            title = { Text("Add Roku Device by IP") },
            text = {
                Column {
                    Text(
                        text = "Enter the IP address of your Roku device (e.g., 192.168.1.72):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ipInputText,
                        onValueChange = { ipInputText = it },
                        label = { Text("IP Address") },
                        placeholder = { Text("192.168.1.72") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ip = ipInputText.trim()
                        if (ip.isNotBlank()) {
                            showAddByIpDialog = false
                            viewModel.addDeviceByIp(ip) { deviceId ->
                                onDeviceClick(deviceId)
                            }
                        }
                    },
                    enabled = ipInputText.isNotBlank()
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddByIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart TV Privacy Sentinel") },
                actions = {
                    IconButton(
                        onClick = {
                            ipInputText = ""
                            showAddByIpDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add by IP")
                    }
                    IconButton(
                        onClick = { permissionLauncher.launch(permissionsToRequest) },
                        enabled = !uiState.isScanning
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    }
                }
            )
        },
        snackbarHost = {
            uiState.errorMessage?.let { message ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(message)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!uiState.isWifiConnected) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Wi-Fi disconnected. Connect to the same Wi-Fi network as your TV to scan",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { permissionLauncher.launch(permissionsToRequest) }
                                ) {
                                    Text("Retry")
                                }
                                OutlinedButton(
                                    onClick = {
                                        ipInputText = ""
                                        showAddByIpDialog = true
                                    }
                                ) {
                                    Text("Add by IP")
                                }
                            }
                        }
                    }
                }
            } else if (uiState.discoveredDevices.isEmpty() && !uiState.isScanning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Roku devices found on Wi-Fi.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { permissionLauncher.launch(permissionsToRequest) }) {
                                Text("Start Discovery")
                            }
                            OutlinedButton(
                                onClick = {
                                    ipInputText = ""
                                    showAddByIpDialog = true
                                }
                            ) {
                                Text("Add by IP")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.discoveredDevices,
                        key = { it.device.deviceId }
                    ) { deviceWithChecks ->
                        DeviceCard(
                            deviceWithChecks = deviceWithChecks,
                            onClick = { onDeviceClick(deviceWithChecks.device.deviceId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    deviceWithChecks: RokuDeviceWithChecks,
    onClick: () -> Unit
) {
    val device = deviceWithChecks.device
    val verifiedCount = deviceWithChecks.checks.count { it.isVerified }
    val totalCount = deviceWithChecks.checks.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.isTv) Icons.Default.Tv else Icons.Default.TvOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${device.modelName} (${device.ipAddress})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = if (device.isTv) "Roku TV" else "Streaming Player"
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$verifiedCount / $totalCount verified",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (verifiedCount == totalCount && totalCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }
        }
    }
}

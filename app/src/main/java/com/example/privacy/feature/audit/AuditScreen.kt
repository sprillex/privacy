package com.example.privacy.feature.audit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.privacy.core.database.PrivacyCheckItemEntity
import com.example.privacy.feature.privacy.NavigationProgressState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    viewModel: AuditViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val deviceWithChecks = uiState.deviceWithChecks
    val device = deviceWithChecks?.device

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Device Audit") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        if (deviceWithChecks == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Privacy Score Card
                PrivacyScoreCard(deviceWithChecks.checks)

                // Macro Progress Indicator
                if (uiState.macroState is NavigationProgressState.Running) {
                    val runningState = uiState.macroState as NavigationProgressState.Running
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = runningState.macroName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Step ${runningState.currentStep}/${runningState.totalSteps}: ${runningState.stepName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { runningState.currentStep.toFloat() / runningState.totalSteps },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else if (uiState.macroState is NavigationProgressState.Error) {
                    val errorState = uiState.macroState as NavigationProgressState.Error
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Action Card 1: Advertising Tracking
                val adTrackingCheck = deviceWithChecks.checks.find { it.targetKey == "AD_TRACKING" }
                if (adTrackingCheck != null) {
                    ActionCard(
                        title = "Advertising Tracking",
                        description = "Limit ad tracking and reset your advertising ID to prevent cross-app profile tracking.",
                        confirmationText = "I have checked 'Limit ad tracking'",
                        checkItem = adTrackingCheck,
                        onOpenOnTvClick = { viewModel.triggerAdTrackingMacro() },
                        onToggleChange = { isChecked ->
                            viewModel.toggleCheckItemVerification(adTrackingCheck, isChecked)
                        }
                    )
                }

                // Action Card 2: Smart TV Experience / ACR (Only visible if isTv == true)
                if (device?.isTv == true) {
                    val acrCheck = deviceWithChecks.checks.find { it.targetKey == "ACR" }
                    if (acrCheck != null) {
                        ActionCard(
                            title = "Smart TV Experience (ACR)",
                            description = "Automatic Content Recognition analyzes TV viewing habits. Disable 'Use info from TV inputs' to protect viewing privacy.",
                            confirmationText = "I have unchecked 'Use info from TV inputs'",
                            checkItem = acrCheck,
                            onOpenOnTvClick = { viewModel.triggerAcrMacro() },
                            onToggleChange = { isChecked ->
                                viewModel.toggleCheckItemVerification(acrCheck, isChecked)
                            }
                        )
                    }
                }

                // Mini Remote Controls
                MiniRemoteCard(onSendKey = { key -> viewModel.sendRemoteKey(key) })
            }
        }
    }
}

@Composable
fun PrivacyScoreCard(checks: List<PrivacyCheckItemEntity>) {
    val total = checks.size
    val verified = checks.count { it.isVerified }
    val progress = if (total > 0) verified.toFloat() / total else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Privacy Score",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$verified of $total privacy targets verified",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    description: String,
    confirmationText: String,
    checkItem: PrivacyCheckItemEntity,
    onOpenOnTvClick: () -> Unit,
    onToggleChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenOnTvClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Tv, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open on TV")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = checkItem.isVerified,
                    onCheckedChange = onToggleChange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = confirmationText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun MiniRemoteCard(onSendKey: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "In-App Remote Fallback",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            // D-Pad layout
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSendKey("Home") }) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }
                OutlinedButton(onClick = { onSendKey("Up") }) {
                    Text("▲")
                }
                IconButton(onClick = { onSendKey("Back") }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Back")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { onSendKey("Left") }) {
                    Text("◄")
                }
                Button(onClick = { onSendKey("Select") }) {
                    Text("OK")
                }
                OutlinedButton(onClick = { onSendKey("Right") }) {
                    Text("►")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = { onSendKey("Down") }) {
                Text("▼")
            }
        }
    }
}

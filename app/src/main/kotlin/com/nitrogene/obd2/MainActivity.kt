package com.nitrogene.obd2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitrogene.obd2.core.obd.transport.TransportState
import com.nitrogene.obd2.diag.AppDiagLog
import com.nitrogene.obd2.ui.ObdViewModel
import com.nitrogene.obd2.ui.screens.BenchScreen
import com.nitrogene.obd2.ui.screens.ConnectScreen
import com.nitrogene.obd2.ui.screens.LiveDashboardScreen
import com.nitrogene.obd2.ui.screens.RecorderScreen
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

private enum class TopLevelTab(
    val label: String,
    val icon: ImageVector,
) {
    Connect("Connect", Icons.Default.Bluetooth),
    Live("Live", Icons.Default.Speed),
    Recorder("Recorder", Icons.Default.FiberManualRecord),
    Bench("Bench", Icons.Default.Build),
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Obd2AppShell()
            }
        }
    }
}

@Composable
private fun Obd2AppShell(
    viewModel: ObdViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(TopLevelTab.Connect) }

    val transportState by viewModel.transportState.collectAsStateWithLifecycle()
    val selectedProtocol by viewModel.selectedProtocol.collectAsStateWithLifecycle()
    val sessionInfo by viewModel.sessionInfo.collectAsStateWithLifecycle()
    val vin by viewModel.vin.collectAsStateWithLifecycle()
    val batteryVoltageV by viewModel.batteryVoltageV.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isScanningBle by viewModel.isScanningBle.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()

    val isPollingLive by viewModel.isPollingLive.collectAsStateWithLifecycle()
    val livePids by viewModel.livePids.collectAsStateWithLifecycle()
    val confirmedDtcs by viewModel.confirmedDtcs.collectAsStateWithLifecycle()
    val pendingDtcs by viewModel.pendingDtcs.collectAsStateWithLifecycle()
    val freezeFrameDtc by viewModel.freezeFrameDtc.collectAsStateWithLifecycle()

    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val extendedUnlocked by viewModel.extendedUnlocked.collectAsStateWithLifecycle()
    val consoleLog by viewModel.consoleLog.collectAsStateWithLifecycle()
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    if (showAboutDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("OBD2 Companion — About") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                            "Built: ${BuildConfig.BUILD_TIME}\n" +
                            "Package: ${BuildConfig.APPLICATION_ID}",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { AppDiagLog.copyToClipboard(context) },
                        ) {
                            Text("Copy Logs")
                        }
                        TextButton(
                            onClick = { AppDiagLog.shareText(context) },
                        ) {
                            Text("Share Logs")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            },
        )
    }

    val transportLabel = when (val state = transportState) {
        is TransportState.Disconnected -> "Disconnected"
        is TransportState.Connecting -> "Connecting (${state.type.name})"
        is TransportState.Connected -> "${state.type.name}: ${state.endpoint}"
        is TransportState.Error -> "Error: ${state.message}"
    }
    val voltageLabel = batteryVoltageV?.let {
        String.format(Locale.US, "%.1f V", it)
    } ?: "--.- V"

    Scaffold(
        topBar = {
            TransportStatusBanner(
                transportLabel = transportLabel,
                voltageLabel = voltageLabel,
                canDisconnect = transportState is TransportState.Connected ||
                    transportState is TransportState.Connecting,
                onDisconnectClick = viewModel::disconnect,
                onAboutClick = { showAboutDialog = true },
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                TopLevelTab.Connect -> ConnectScreen(
                    transportState = transportState,
                    selectedProtocol = selectedProtocol,
                    sessionInfo = sessionInfo,
                    vin = vin,
                    statusMessage = statusMessage,
                    isScanningBle = isScanningBle,
                    discoveredDevices = discoveredDevices,
                    onSelectProtocol = viewModel::selectProtocol,
                    onStartBleScan = { includeAll -> viewModel.startBleScan(includeAll) },
                    onStopBleScan = viewModel::stopBleScan,
                    onConnectBle = { viewModel.connectBle(it.device) },
                    onConnectWifi = viewModel::connectWifi,
                    onDisconnect = viewModel::disconnect,
                )

                TopLevelTab.Live -> LiveDashboardScreen(
                    transportState = transportState,
                    isKLine = sessionInfo?.isKLine ?: selectedProtocol.isKLine,
                    isPollingLive = isPollingLive,
                    livePids = livePids,
                    confirmedDtcs = confirmedDtcs,
                    pendingDtcs = pendingDtcs,
                    freezeFrameDtc = freezeFrameDtc,
                    onTogglePolling = viewModel::toggleLivePolling,
                    onRefreshDtcs = viewModel::refreshDtcs,
                    onClearDtcs = viewModel::clearDtcs,
                )

                TopLevelTab.Recorder -> RecorderScreen(
                    transportState = transportState,
                    recorderState = recorderState,
                    profiles = profiles,
                    captures = captures,
                    settings = settings,
                    onSelectProfile = { id -> viewModel.updateRecorderSettings(selectedProfileId = id) },
                    onUpdateTPre = { sec -> viewModel.updateRecorderSettings(tPreSeconds = sec) },
                    onUpdateTPost = { sec -> viewModel.updateRecorderSettings(tPostSeconds = sec) },
                    onToggleGps = { enabled -> viewModel.updateRecorderSettings(gpsContextEnabled = enabled) },
                    onSaveProfile = viewModel::saveProfile,
                    onToggleRecorder = viewModel::toggleRecorderService,
                    onMarkManualEvent = viewModel::markManualEvent,
                    onCopyJson = viewModel::copyCaptureJson,
                    onShareBundle = viewModel::shareCaptureBundle,
                    onDeleteCapture = viewModel::deleteCapture,
                )

                TopLevelTab.Bench -> BenchScreen(
                    transportState = transportState,
                    extendedUnlocked = extendedUnlocked,
                    consoleLog = consoleLog,
                    onSendCommand = viewModel::sendAtCommand,
                )
            }
        }
    }
}

@Composable
private fun TransportStatusBanner(
    transportLabel: String,
    voltageLabel: String,
    canDisconnect: Boolean,
    onDisconnectClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = transportLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "VBAT: $voltageLabel",
                style = MaterialTheme.typography.labelLarge,
            )
            if (canDisconnect) {
                IconButton(onClick = onDisconnectClick) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = "Disconnect",
                    )
                }
            }
            IconButton(onClick = onAboutClick) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About",
                )
            }
        }
    }
}

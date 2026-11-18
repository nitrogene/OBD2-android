package com.nitrogene.obd2.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nitrogene.obd2.core.obd.protocol.ElmProtocol
import com.nitrogene.obd2.core.obd.protocol.ElmSessionInfo
import com.nitrogene.obd2.core.obd.transport.TransportState
import com.nitrogene.obd2.transport.ble.BleDiscoveredDevice
import com.nitrogene.obd2.transport.ble.BlePermissions
import com.nitrogene.obd2.transport.wifi.WifiConstants

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    transportState: TransportState,
    selectedProtocol: ElmProtocol,
    sessionInfo: ElmSessionInfo?,
    vin: String?,
    statusMessage: String?,
    isScanningBle: Boolean,
    discoveredDevices: List<BleDiscoveredDevice>,
    onSelectProtocol: (ElmProtocol) -> Unit,
    onStartBleScan: () -> Unit,
    onStopBleScan: () -> Unit,
    onConnectBle: (BleDiscoveredDevice) -> Unit,
    onConnectWifi: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    var wifiHost by rememberSaveable { mutableStateOf(WifiConstants.DEFAULT_HOST) }
    var wifiPortText by rememberSaveable { mutableStateOf(WifiConstants.DEFAULT_PORT.toString()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            onStartBleScan()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Connection", style = MaterialTheme.typography.headlineSmall)
            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (transportState is TransportState.Connected) {
            item {
                SessionSummaryCard(
                    endpoint = transportState.endpoint,
                    sessionInfo = sessionInfo,
                    vin = vin,
                    onDisconnect = onDisconnect,
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("ELM327 Protocol", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ElmProtocol.entries.forEach { protocol ->
                            FilterChip(
                                selected = selectedProtocol == protocol,
                                onClick = { onSelectProtocol(protocol) },
                                label = { Text("ATSP${protocol.spCode}: ${protocol.name}") },
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Wi-Fi SoftAP (${WifiConstants.DEFAULT_SSID})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = wifiHost,
                            onValueChange = { wifiHost = it },
                            label = { Text("Host") },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                        )
                        OutlinedTextField(
                            value = wifiPortText,
                            onValueChange = { wifiPortText = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        onClick = {
                            val port = wifiPortText.toIntOrNull() ?: WifiConstants.DEFAULT_PORT
                            onConnectWifi(wifiHost.trim(), port)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Connect Wi-Fi TCP")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("BLE 5.0 GATT (OBD2-ELM327)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val required = buildList {
                                    addAll(BlePermissions.requiredScanPermissions())
                                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.distinct().toTypedArray()

                                if (BlePermissions.hasScanPermissions(context)) {
                                    onStartBleScan()
                                } else {
                                    permissionLauncher.launch(required)
                                }
                            },
                            enabled = !isScanningBle,
                        ) {
                            Text(if (isScanningBle) "Scanning..." else "Scan BLE")
                        }
                        if (isScanningBle) {
                            OutlinedButton(onClick = onStopBleScan) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }

        items(discoveredDevices, key = { it.address }) { item ->
            @SuppressLint("MissingPermission")
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${item.address} · ${item.rssi} dBm",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = { onConnectBle(item) }) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryCard(
    endpoint: String,
    sessionInfo: ElmSessionInfo?,
    vin: String?,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Active Session: $endpoint", style = MaterialTheme.typography.titleMedium)
            if (sessionInfo != null) {
                Text("Adapter: ${sessionInfo.elmVersion}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Protocol: ${sessionInfo.protocolDescription} (K-Line=${sessionInfo.isKLine})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Supported PIDs: ${sessionInfo.supportedPids.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("VIN: ${vin ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Disconnect")
            }
        }
    }
}

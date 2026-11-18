package com.nitrogene.obd2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nitrogene.obd2.core.obd.protocol.DecodedPidValue
import com.nitrogene.obd2.core.obd.transport.TransportState
import java.util.Locale

@Composable
fun LiveDashboardScreen(
    transportState: TransportState,
    isKLine: Boolean,
    isPollingLive: Boolean,
    livePids: Map<Int, DecodedPidValue>,
    confirmedDtcs: List<String>,
    pendingDtcs: List<String>,
    freezeFrameDtc: String?,
    onTogglePolling: () -> Unit,
    onRefreshDtcs: () -> Unit,
    onClearDtcs: () -> Unit,
) {
    val connected = transportState is TransportState.Connected
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear DTCs (Mode 04)?") },
            text = {
                Text(
                    "This clears confirmed/pending DTCs, Freeze Frame, and resets emission readiness monitors.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearDtcs()
                    },
                ) {
                    Text("Clear DTCs")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Live Dashboard", style = MaterialTheme.typography.headlineSmall)
                    if (isKLine) {
                        Text(
                            "K-Line 10.4 kbps guard active (<= 8 PIDs)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Button(onClick = onTogglePolling, enabled = connected) {
                    Text(if (isPollingLive) "Stop Poll" else "Start Poll")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Diagnostic Trouble Codes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Confirmed (Mode 03): ${if (confirmedDtcs.isEmpty()) "None" else confirmedDtcs.joinToString()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Pending (Mode 07): ${if (pendingDtcs.isEmpty()) "None" else pendingDtcs.joinToString()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Freeze Frame (Mode 02): ${freezeFrameDtc ?: "None"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRefreshDtcs, enabled = connected) {
                            Text("Read DTCs")
                        }
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            enabled = connected,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Clear (Mode 04)")
                        }
                    }
                }
            }
        }

        val pidList = livePids.values.sortedBy { it.pid }
        if (pidList.isEmpty()) {
            item {
                Text(
                    if (connected) "Tap 'Start Poll' to stream live PIDs." else "Connect to a dongle first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(pidList, key = { it.pid }) { pid ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(pid.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "PID 0x${pid.pid.toString(16).uppercase().padStart(2, '0')}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = "${String.format(Locale.US, "%.2f", pid.value)} ${pid.unit}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
    }
}

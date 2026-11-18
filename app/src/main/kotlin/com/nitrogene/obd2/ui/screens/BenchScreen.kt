package com.nitrogene.obd2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nitrogene.obd2.core.obd.protocol.DiagnosticSecurityGate
import com.nitrogene.obd2.core.obd.transport.TransportState
import com.nitrogene.obd2.ui.ConsoleEntry

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchScreen(
    transportState: TransportState,
    extendedUnlocked: Boolean,
    consoleLog: List<ConsoleEntry>,
    onSendCommand: (command: String, confirmedExtended: Boolean) -> Unit,
) {
    val connected = transportState is TransportState.Connected
    var pendingRestrictedCmd by rememberSaveable { mutableStateOf<String?>(null) }
    var showConsoleDialog by rememberSaveable { mutableStateOf(false) }

    pendingRestrictedCmd?.let { cmd ->
        AlertDialog(
            onDismissRequest = { pendingRestrictedCmd = null },
            title = { Text("Confirm Extended / Restricted Command") },
            text = {
                Text(
                    "Command '$cmd' unlocks AT+EXT or modifies ECU state. Only proceed in a safe bench/diagnostic state.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestrictedCmd = null
                        onSendCommand(cmd, true)
                    },
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestrictedCmd = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showConsoleDialog) {
        AtConsoleDialog(
            connected = connected,
            consoleLog = consoleLog,
            onSendCommand = { cmd ->
                if (DiagnosticSecurityGate.requiresExtendedConfirmation(cmd)) {
                    pendingRestrictedCmd = cmd
                } else {
                    onSendCommand(cmd, false)
                }
            },
            onDismiss = { showConsoleDialog = false },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("VIRTUAL_BENCH & Extended Tools", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("ECU Simulator (AT+SIM:...)", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = { onSendCommand("AT+SIM:START", false) },
                            enabled = connected,
                        ) { Text("SIM START") }
                        OutlinedButton(
                            onClick = { onSendCommand("AT+SIM:STOP", false) },
                            enabled = connected,
                        ) { Text("SIM STOP") }
                        OutlinedButton(
                            onClick = { onSendCommand("AT+SIM:RPM=2800", false) },
                            enabled = connected,
                        ) { Text("RPM=2800") }
                        OutlinedButton(
                            onClick = { onSendCommand("AT+SIM:SPD=80", false) },
                            enabled = connected,
                        ) { Text("SPD=80") }
                        OutlinedButton(
                            onClick = { onSendCommand("AT+SIM:CLT=95", false) },
                            enabled = connected,
                        ) { Text("CLT=95°C") }
                        OutlinedButton(
                            onClick = { onSendCommand("AT+SIM:VBAT=11400", false) },
                            enabled = connected,
                        ) { Text("VBAT=11.4V") }
                        Button(
                            onClick = { onSendCommand("AT+SIM:DTC=P0131", false) },
                            enabled = connected,
                        ) { Text("Inject P0131") }
                        Button(
                            onClick = { onSendCommand("AT+SIM:DTC=P0301", false) },
                            enabled = connected,
                        ) { Text("Inject P0301") }
                        OutlinedButton(
                            onClick = { onSendCommand("AT+SIM:DTC=CLEAR", false) },
                            enabled = connected,
                        ) { Text("Clear SIM DTC") }
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
                    Text("Dongle Diagnostics & Security Gate", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Extended mode (AT+EXT): ${if (extendedUnlocked) "UNLOCKED" else "LOCKED"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onSendCommand("AT+STATS", false) },
                            enabled = connected,
                        ) { Text("AT+STATS") }
                        Button(
                            onClick = {
                                if (extendedUnlocked) {
                                    onSendCommand("AT+EXT=0", false)
                                } else {
                                    pendingRestrictedCmd = "AT+EXT=1"
                                }
                            },
                            enabled = connected,
                        ) {
                            Text(if (extendedUnlocked) "Lock AT+EXT=0" else "Unlock AT+EXT=1")
                        }
                        OutlinedButton(
                            onClick = { showConsoleDialog = true },
                        ) { Text("Open AT Console") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtConsoleDialog(
    connected: Boolean,
    consoleLog: List<ConsoleEntry>,
    onSendCommand: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Raw AT / OBD Console") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(consoleLog) { entry ->
                        Text(
                            text = "> ${entry.command}\n${entry.response}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Command (e.g. ATRV)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val cmd = input.trim()
                            if (cmd.isNotEmpty()) {
                                onSendCommand(cmd)
                                input = ""
                            }
                        },
                        enabled = connected,
                    ) {
                        Text("Send")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

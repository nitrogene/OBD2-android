package com.nitrogene.obd2.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nitrogene.obd2.core.obd.recorder.DtcPidProfile
import com.nitrogene.obd2.core.obd.recorder.FaultCapture
import com.nitrogene.obd2.core.obd.recorder.RecorderState
import com.nitrogene.obd2.core.obd.transport.TransportState
import com.nitrogene.obd2.data.RecorderSettings
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecorderScreen(
    transportState: TransportState,
    recorderState: RecorderState,
    profiles: List<DtcPidProfile>,
    captures: List<FaultCapture>,
    settings: RecorderSettings,
    onSelectProfile: (String) -> Unit,
    onUpdateTPre: (Int) -> Unit,
    onUpdateTPost: (Int) -> Unit,
    onToggleGps: (Boolean) -> Unit,
    onSaveProfile: (DtcPidProfile) -> Unit,
    onToggleRecorder: () -> Unit,
    onMarkManualEvent: () -> Unit,
    onCopyJson: (FaultCapture) -> Unit,
    onShareBundle: (FaultCapture) -> Unit,
    onDeleteCapture: (String) -> Unit,
) {
    val connected = transportState is TransportState.Connected
    val isArmedOrTriggered = recorderState is RecorderState.Armed || recorderState is RecorderState.Triggered
    val selectedProfile = profiles.firstOrNull { it.id == settings.selectedProfileId } ?: profiles.firstOrNull()

    var editingProfile by remember { mutableStateOf<DtcPidProfile?>(null) }
    var inspectingCapture by remember { mutableStateOf<FaultCapture?>(null) }

    editingProfile?.let { draft ->
        ProfileEditorDialog(
            initial = draft,
            onDismiss = { editingProfile = null },
            onSave = { updated ->
                onSaveProfile(updated)
                onSelectProfile(updated.id)
                editingProfile = null
            },
        )
    }

    inspectingCapture?.let { capture ->
        CaptureDetailDialog(
            capture = capture,
            onDismiss = { inspectingCapture = null },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Fault Context Recorder", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = when (recorderState) {
                            is RecorderState.Idle -> "State: Idle"
                            is RecorderState.Armed ->
                                "State: Armed (${recorderState.profile.name}) — ${recorderState.bufferedSamples} samples"
                            is RecorderState.Triggered ->
                                "State: TRIGGERED (${recorderState.reason}) — ${recorderState.remainingPostMs / 1000}s remaining"
                            is RecorderState.Completed ->
                                "State: Completed (${recorderState.capture.samples.size} samples saved)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onToggleRecorder,
                            enabled = connected || isArmedOrTriggered,
                        ) {
                            Text(if (isArmedOrTriggered) "Stop Recorder" else "Arm Recorder")
                        }
                        if (recorderState is RecorderState.Armed) {
                            OutlinedButton(onClick = onMarkManualEvent) {
                                Text("Mark Event")
                            }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("DTC-to-PID Profile", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (selectedProfile != null) {
                                TextButton(onClick = { editingProfile = selectedProfile }) {
                                    Text("Edit")
                                }
                            }
                            TextButton(
                                onClick = {
                                    editingProfile = DtcPidProfile(
                                        id = "custom_${UUID.randomUUID().toString().take(6)}",
                                        name = "Custom Profile",
                                        dtcPattern = "P0*",
                                        pids = listOf(0x05, 0x0C, 0x0D, 0x14),
                                    )
                                },
                            ) {
                                Text("New")
                            }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profiles.forEach { profile ->
                            FilterChip(
                                selected = profile.id == selectedProfile?.id,
                                onClick = { onSelectProfile(profile.id) },
                                label = { Text(profile.name) },
                            )
                        }
                    }

                    Text("Pre-fault buffer (T_pre): ${settings.tPreSeconds}s")
                    Slider(
                        value = settings.tPreSeconds.toFloat(),
                        onValueChange = { onUpdateTPre(it.toInt()) },
                        valueRange = 15f..300f,
                    )

                    Text("Post-fault window (T_post): ${settings.tPostSeconds}s")
                    Slider(
                        value = settings.tPostSeconds.toFloat(),
                        onValueChange = { onUpdateTPost(it.toInt()) },
                        valueRange = 10f..120f,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Attach GPS location context", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.gpsContextEnabled,
                            onCheckedChange = onToggleGps,
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Saved Captures (${captures.size})",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        items(captures, key = { it.metadata.captureId }) { capture ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(capture.faultSummary.triggerReason, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${capture.metadata.capturedAtIso} · ${capture.samples.size} samples · " +
                            "DTCs: ${capture.faultSummary.confirmedDtcs.joinToString().ifEmpty { "None" }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onCopyJson(capture) }) {
                            Text("Copy JSON for AI")
                        }
                        OutlinedButton(onClick = { onShareBundle(capture) }) {
                            Text("Share JSON + CSV")
                        }
                        TextButton(onClick = { inspectingCapture = capture }) {
                            Text("Details")
                        }
                        TextButton(onClick = { onDeleteCapture(capture.metadata.captureId) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    initial: DtcPidProfile,
    onDismiss: () -> Unit,
    onSave: (DtcPidProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var dtcPattern by remember { mutableStateOf(initial.dtcPattern) }
    var pidHexList by remember {
        mutableStateOf(
            initial.pids.joinToString(",") { it.toString(16).uppercase().padStart(2, '0') },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit DTC-to-PID Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dtcPattern,
                    onValueChange = { dtcPattern = it },
                    label = { Text("DTC Pattern (e.g. P0131)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pidHexList,
                    onValueChange = { pidHexList = it },
                    label = { Text("Mode 01 PIDs (hex, comma-separated)") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedPids = pidHexList.split(',')
                        .mapNotNull { it.trim().removePrefix("0x").toIntOrNull(16) }
                        .distinct()
                        .ifEmpty { initial.pids }
                    onSave(
                        initial.copy(
                            name = name.trim().ifEmpty { initial.name },
                            dtcPattern = dtcPattern.trim().ifEmpty { initial.dtcPattern },
                            pids = parsedPids,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CaptureDetailDialog(
    capture: FaultCapture,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Capture ${capture.metadata.captureId.take(8)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Time: ${capture.metadata.capturedAtIso}")
                Text("VIN: ${capture.metadata.vin ?: "Unknown"}")
                Text("Protocol: ${capture.metadata.protocolDescription}")
                Text("Trigger: ${capture.faultSummary.triggerReason}")
                Text("Confirmed DTCs: ${capture.faultSummary.confirmedDtcs.joinToString().ifEmpty { "None" }}")
                Text("Pending DTCs: ${capture.faultSummary.pendingDtcs.joinToString().ifEmpty { "None" }}")
                Text("Freeze Frame DTC: ${capture.faultSummary.freezeFrameDtc ?: "None"}")
                Text("Samples: ${capture.samples.size}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

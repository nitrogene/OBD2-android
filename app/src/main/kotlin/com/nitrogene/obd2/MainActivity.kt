package com.nitrogene.obd2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint

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
private fun Obd2AppShell() {
    var selectedTab by rememberSaveable { mutableStateOf(TopLevelTab.Connect) }

    Scaffold(
        topBar = {
            TransportStatusBanner(
                transportLabel = "Disconnected",
                voltageLabel = "--.- V",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = selectedTab.label,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Phase 1 bootstrap ready.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TransportStatusBanner(
    transportLabel: String,
    voltageLabel: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Transport: $transportLabel",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "VBAT: $voltageLabel",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

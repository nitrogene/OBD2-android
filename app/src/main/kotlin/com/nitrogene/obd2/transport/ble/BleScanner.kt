package com.nitrogene.obd2.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Discovered BLE OBD-II dongle metadata.
 */
data class BleDiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice,
    val matchesObd2: Boolean = true,
)

/**
 * Scans for `OBD2-ELM327` dongles advertising the `0xFFF0` or `0xFFE0` UART GATT services.
 */
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun scanDevices(includeAll: Boolean = false): Flow<BleDiscoveredDevice> = callbackFlow {
        if (!BlePermissions.hasScanPermissions(context)) {
            close(SecurityException("Missing BLE scan permissions"))
            return@callbackFlow
        }

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            close(IllegalStateException("Bluetooth adapter is unavailable or disabled"))
            return@callbackFlow
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val fff0 = ParcelUuid(BleConstants.SERVICE_FFF0)
        val ffe0 = ParcelUuid(BleConstants.SERVICE_FFE0)

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val uuids = result.scanRecord?.serviceUuids.orEmpty()
                val rawName = result.scanRecord?.deviceName ?: device.name
                val matchesUuid = fff0 in uuids || ffe0 in uuids
                val matchesName = rawName != null &&
                    (rawName.contains("OBD", ignoreCase = true) ||
                        rawName.contains("ELM", ignoreCase = true))
                val matchesObd2 = matchesUuid || matchesName
                if (!includeAll && !matchesObd2) return

                trySend(
                    BleDiscoveredDevice(
                        name = rawName ?: if (matchesObd2) BleConstants.DEFAULT_DEVICE_NAME else "Unnamed BLE",
                        address = device.address,
                        rssi = result.rssi,
                        device = device,
                        matchesObd2 = matchesObd2,
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed with errorCode=$errorCode"))
            }
        }

        scanner.startScan(null, settings, callback)
        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }
}

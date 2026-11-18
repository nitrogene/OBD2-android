package com.nitrogene.obd2.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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
)

/**
 * Scans for `OBD2-ELM327` dongles advertising the `0xFFF0` or `0xFFE0` UART GATT services.
 */
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun scanDevices(): Flow<BleDiscoveredDevice> = callbackFlow {
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

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleConstants.SERVICE_FFF0)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleConstants.SERVICE_FFE0)).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = result.scanRecord?.deviceName ?: device.name ?: BleConstants.DEFAULT_DEVICE_NAME
                trySend(
                    BleDiscoveredDevice(
                        name = name,
                        address = device.address,
                        rssi = result.rssi,
                        device = device,
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed with errorCode=$errorCode"))
            }
        }

        scanner.startScan(filters, settings, callback)
        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }
}

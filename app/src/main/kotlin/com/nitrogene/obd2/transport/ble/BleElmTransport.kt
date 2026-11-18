package com.nitrogene.obd2.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.nitrogene.obd2.core.obd.transport.ElmCommandExchange
import com.nitrogene.obd2.core.obd.transport.ElmTransport
import com.nitrogene.obd2.core.obd.transport.TransportDisconnectedException
import com.nitrogene.obd2.core.obd.transport.TransportState
import com.nitrogene.obd2.core.obd.transport.TransportType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/**
 * Native Android BLE 5.0 GATT implementation of [ElmTransport] for `OBD2-ELM327`.
 *
 * Connects to primary UART service `0xFFF0` (`0xFFF1` Notify / `0xFFF2` Write) or falls back
 * to alias service `0xFFE0` (`0xFFE1` Notify + Write), negotiates ATT MTU >= 247, and enables
 * CCCD notifications before sending commands.
 */
class BleElmTransport(
    private val context: Context,
    private val device: BluetoothDevice,
    private val connectTimeoutMs: Long = 10_000L,
) : ElmTransport {

    override val type: TransportType = TransportType.BLE

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val exchange = ElmCommandExchange()

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var negotiatedPayloadSize: Int = 20

    @Volatile
    private var readyDeferred: CompletableDeferred<Unit>? = null

    @Volatile
    private var writeDeferred: CompletableDeferred<Unit>? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        if (!BlePermissions.hasConnectPermissions(context)) {
            throw SecurityException("Missing BLUETOOTH_CONNECT permission")
        }

        val endpoint = "${device.name ?: BleConstants.DEFAULT_DEVICE_NAME} (${device.address})"
        _state.value = TransportState.Connecting(TransportType.BLE, endpoint)

        val setupDone = CompletableDeferred<Unit>()
        readyDeferred = setupDone

        val callback = createGattCallback(endpoint, setupDone)
        val connectedGatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: run {
                _state.value = TransportState.Error("connectGatt returned null")
                throw TransportDisconnectedException("connectGatt returned null")
            }
        gatt = connectedGatt

        try {
            withTimeout(connectTimeoutMs) {
                setupDone.await()
            }
            _state.value = TransportState.Connected(TransportType.BLE, endpoint)
        } catch (t: Throwable) {
            disconnect()
            _state.value = TransportState.Error(t.message ?: "BLE connection failed", t)
            throw t
        } finally {
            readyDeferred = null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        val currentGatt = gatt
        gatt = null
        writeCharacteristic = null
        readyDeferred?.cancel()
        readyDeferred = null
        writeDeferred?.cancel()
        writeDeferred = null
        exchange.onLinkLost("BLE transport disconnected")
        if (currentGatt != null) {
            runCatching { currentGatt.disconnect() }
            runCatching { currentGatt.close() }
        }
        _state.value = TransportState.Disconnected
    }

    override suspend fun sendCommand(command: String, timeoutMs: Long): String {
        if (gatt == null || writeCharacteristic == null || _state.value !is TransportState.Connected) {
            throw TransportDisconnectedException("BLE transport is not connected")
        }
        return exchange.execute(command, timeoutMs) { payload ->
            writeChunked(payload)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeChunked(payload: ByteArray) {
        val currentGatt = gatt ?: throw TransportDisconnectedException("BLE GATT closed")
        val char = writeCharacteristic ?: throw TransportDisconnectedException("BLE write characteristic missing")
        val chunkSize = negotiatedPayloadSize.coerceAtLeast(20)
        val useNoResponse = (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val writeType = if (useNoResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        var offset = 0
        while (offset < payload.size) {
            val end = (offset + chunkSize).coerceAtMost(payload.size)
            val chunk = payload.copyOfRange(offset, end)
            val ack = CompletableDeferred<Unit>()
            writeDeferred = ack

            val initiated = writeCharacteristicCompat(currentGatt, char, chunk, writeType)
            if (!initiated) {
                writeDeferred = null
                throw TransportDisconnectedException("Failed to initiate BLE GATT write")
            }
            withTimeout(3_000L) {
                ack.await()
            }
            offset = end
        }
    }

    @SuppressLint("MissingPermission")
    private fun createGattCallback(
        endpoint: String,
        setupDone: CompletableDeferred<Unit>,
    ): BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val err = TransportDisconnectedException("BLE GATT error status=$status")
                setupDone.completeExceptionally(err)
                exchange.onLinkLost("BLE link error status=$status", err)
                _state.value = TransportState.Error("BLE GATT status=$status", err)
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!gatt.discoverServices()) {
                        setupDone.completeExceptionally(
                            TransportDisconnectedException("discoverServices() failed to start"),
                        )
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val err = TransportDisconnectedException("BLE disconnected from $endpoint")
                    if (!setupDone.isCompleted) {
                        setupDone.completeExceptionally(err)
                    }
                    exchange.onLinkLost("BLE disconnected", err)
                    _state.value = TransportState.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setupDone.completeExceptionally(
                    TransportDisconnectedException("Service discovery failed status=$status"),
                )
                return
            }
            if (!gatt.requestMtu(BleConstants.REQUESTED_MTU)) {
                enableUartNotifications(gatt, setupDone)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && mtu > 3) {
                negotiatedPayloadSize = mtu - 3
            }
            enableUartNotifications(gatt, setupDone)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setupDone.complete(Unit)
            } else {
                setupDone.completeExceptionally(
                    TransportDisconnectedException("CCCD write failed status=$status"),
                )
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val pending = writeDeferred
            writeDeferred = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pending?.complete(Unit)
            } else {
                pending?.completeExceptionally(
                    TransportDisconnectedException("GATT write failed status=$status"),
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            exchange.onBytesReceived(value)
        }

        @Deprecated("Deprecated in Android API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value ?: return
                exchange.onBytesReceived(bytes)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableUartNotifications(
        gatt: BluetoothGatt,
        setupDone: CompletableDeferred<Unit>,
    ) {
        val fff0 = gatt.getService(BleConstants.SERVICE_FFF0)
        val ffe0 = gatt.getService(BleConstants.SERVICE_FFE0)

        val (notifyChar, writeChar) = when {
            fff0 != null -> {
                val notify = fff0.getCharacteristic(BleConstants.CHAR_FFF1_NOTIFY)
                val write = fff0.getCharacteristic(BleConstants.CHAR_FFF2_WRITE)
                notify to write
            }

            ffe0 != null -> {
                val shared = ffe0.getCharacteristic(BleConstants.CHAR_FFE1_NOTIFY_WRITE)
                shared to shared
            }

            else -> null to null
        }

        if (notifyChar == null || writeChar == null) {
            setupDone.completeExceptionally(
                TransportDisconnectedException("ELM327 UART GATT service (0xFFF0/0xFFE0) not found"),
            )
            return
        }

        writeCharacteristic = writeChar
        if (!gatt.setCharacteristicNotification(notifyChar, true)) {
            setupDone.completeExceptionally(
                TransportDisconnectedException("setCharacteristicNotification failed"),
            )
            return
        }

        val cccd = notifyChar.getDescriptor(BleConstants.CCCD_UUID)
        if (cccd == null) {
            setupDone.completeExceptionally(
                TransportDisconnectedException("CCCD descriptor 0x2902 missing"),
            )
            return
        }

        val initiated = writeDescriptorCompat(
            gatt,
            cccd,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
        )
        if (!initiated) {
            setupDone.completeExceptionally(
                TransportDisconnectedException("Failed to write CCCD notification descriptor"),
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, writeType) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }
}

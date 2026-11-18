package com.nitrogene.obd2.transport.ble

import java.util.UUID

/**
 * Authoritative BLE 5.0 GATT constants from `OBD2-firmware/android.md`.
 */
object BleConstants {
    const val DEFAULT_DEVICE_NAME: String = "OBD2-ELM327"
    const val REQUESTED_MTU: Int = 256

    val SERVICE_FFF0: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CHAR_FFF1_NOTIFY: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val CHAR_FFF2_WRITE: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    val SERVICE_FFE0: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val CHAR_FFE1_NOTIFY_WRITE: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

package com.nitrogene.obd2.transport.wifi

/**
 * Authoritative Wi-Fi SoftAP / ELM327 TCP constants from `OBD2-firmware/android.md`.
 */
object WifiConstants {
    const val DEFAULT_SSID: String = "OBD2-Scanner"
    const val DEFAULT_HOST: String = "192.168.42.1"
    const val DEFAULT_PORT: Int = 35_000
}

package com.nitrogene.obd2.core.obd.protocol

/**
 * ELM327 protocol selection codes (`ATSP<n>`).
 */
enum class ElmProtocol(
    val spCode: Int,
    val displayName: String,
    val isKLine: Boolean,
) {
    AUTO(0, "Automatic", isKLine = false),
    ISO_9141_2(3, "ISO 9141-2 (10.4 kbps)", isKLine = true),
    KWP2000_5BAUD(4, "ISO 14230-4 KWP2000 (5-baud init)", isKLine = true),
    KWP2000_FAST(5, "ISO 14230-4 KWP2000 (Fast init)", isKLine = true),
    CAN_11_500(6, "ISO 15765-4 CAN (11-bit 500 kbps)", isKLine = false),
    CAN_29_500(7, "ISO 15765-4 CAN (29-bit 500 kbps)", isKLine = false),
    CAN_11_250(8, "ISO 15765-4 CAN (11-bit 250 kbps)", isKLine = false),
    CAN_29_250(9, "ISO 15765-4 CAN (29-bit 250 kbps)", isKLine = false),
    ;

    companion object {
        fun isKLineDescription(atdpResponse: String): Boolean {
            val upper = atdpResponse.uppercase()
            return upper.contains("9141") || upper.contains("14230") || upper.contains("KWP")
        }
    }
}

/**
 * Session metadata discovered during ELM327 initialization (`ATZ` .. `0100` .. `ATRV`).
 */
data class ElmSessionInfo(
    val elmVersion: String,
    val protocolDescription: String,
    val isKLine: Boolean,
    val batteryVoltageV: Double?,
    val supportedPids: Set<Int>,
)

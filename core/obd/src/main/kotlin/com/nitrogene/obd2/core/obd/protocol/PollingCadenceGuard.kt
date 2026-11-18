package com.nitrogene.obd2.core.obd.protocol

/**
 * Enforces K-Line / KWP2000 (`10,400 baud`) polling cadence limits so each sensor in an active
 * capture profile is sampled at `>= 1 Hz` (max 8 PIDs on K-Line).
 */
object PollingCadenceGuard {
    const val MAX_KLINE_PIDS: Int = 8
    const val KLINE_MIN_INTERVAL_MS: Long = 120L
    const val CAN_MIN_INTERVAL_MS: Long = 10L

    data class CadencePlan(
        val effectivePids: List<Int>,
        val droppedPids: List<Int>,
        val interRequestDelayMs: Long,
        val kLineCapped: Boolean,
    )

    fun plan(requestedPids: List<Int>, isKLine: Boolean): CadencePlan {
        val unique = requestedPids.distinct()
        if (!isKLine) {
            return CadencePlan(
                effectivePids = unique,
                droppedPids = emptyList(),
                interRequestDelayMs = CAN_MIN_INTERVAL_MS,
                kLineCapped = false,
            )
        }
        val capped = unique.take(MAX_KLINE_PIDS)
        val dropped = unique.drop(MAX_KLINE_PIDS)
        return CadencePlan(
            effectivePids = capped,
            droppedPids = dropped,
            interRequestDelayMs = KLINE_MIN_INTERVAL_MS,
            kLineCapped = dropped.isNotEmpty(),
        )
    }
}

/**
 * Security gate matching `OBD2-firmware/android.md` §3.2 (`AT+EXT` & state-modifying SIDs).
 */
object DiagnosticSecurityGate {
    val RESTRICTED_SIDS: Set<Int> = setOf(
        0x11, // ECUReset
        0x27, // SecurityAccess
        0x28, // CommunicationControl
        0x2E, // WriteDataByIdentifier
        0x2F, // InputOutputControlByIdentifier
        0x30, // InputOutputControlByLocalIdentifier
        0x31, // RoutineControl
        0x34, // RequestDownload
        0x35, // RequestUpload
        0x36, // TransferData
        0x37, // RequestTransferExit
        0x3B, // WriteDataByLocalIdentifier
        0x3D, // WriteMemoryByAddress
        0x85, // ControlDTCSetting
    )

    fun requiresExtendedConfirmation(command: String): Boolean {
        val trimmed = command.trim().uppercase()
        if (trimmed.startsWith("AT+EXT=1")) return true
        if (trimmed.startsWith("AT")) return false
        val sid = trimmed.take(2).toIntOrNull(16) ?: return false
        return sid in RESTRICTED_SIDS
    }
}

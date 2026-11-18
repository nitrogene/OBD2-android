package com.nitrogene.obd2.core.obd.recorder

import com.nitrogene.obd2.core.obd.ObdCoreInfo
import kotlinx.serialization.Serializable

@Serializable
enum class ThresholdOperator {
    LESS_THAN,
    GREATER_THAN,
}

/**
 * Software threshold rule to catch intermittent glitches before a confirmed MIL is set.
 */
@Serializable
data class PidThresholdRule(
    val id: String,
    val description: String,
    val targetPid: Int,
    val operator: ThresholdOperator,
    val threshold: Double,
    val sustainedMs: Long = 3_000L,
    val minCoolantC: Double? = null,
    val requireClosedLoop: Boolean = false,
)

/**
 * Editable DTC-to-PID rule profile (e.g. `P0131` O2 B1S1 reference profile).
 */
@Serializable
data class DtcPidProfile(
    val id: String,
    val name: String,
    val dtcPattern: String,
    val pids: List<Int>,
    val tPreSeconds: Int = 60,
    val tPostSeconds: Int = 30,
    val thresholdRules: List<PidThresholdRule> = emptyList(),
)

/**
 * Best-effort GPS context captured alongside PID samples or fault triggers.
 */
@Serializable
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double? = null,
    val speedKmh: Double? = null,
    val accuracyM: Float? = null,
)

@Serializable
data class TelemetrySample(
    val timestampMs: Long,
    val tRelMs: Long = 0L,
    val vbatV: Double? = null,
    val pidValues: Map<Int, Double> = emptyMap(),
    val location: LocationSnapshot? = null,
)

@Serializable
data class FaultSummary(
    val triggerReason: String,
    val confirmedDtcs: List<String> = emptyList(),
    val pendingDtcs: List<String> = emptyList(),
    val freezeFrameDtc: String? = null,
    val milOn: Boolean = false,
)

@Serializable
data class CaptureMetadata(
    val captureId: String,
    val capturedAtIso: String,
    val vin: String? = null,
    val protocolDescription: String,
    val profileId: String,
    val tPreSeconds: Int,
    val tPostSeconds: Int,
    val schemaVersion: Int = ObdCoreInfo.SCHEMA_VERSION,
)

@Serializable
data class FaultCapture(
    val metadata: CaptureMetadata,
    val faultSummary: FaultSummary,
    val samples: List<TelemetrySample>,
)

object DefaultProfiles {
    val P0131_O2_B1S1 = DtcPidProfile(
        id = "p0131_o2_b1s1",
        name = "P0131 — O2 B1S1 Low Voltage (Kalos K-Line)",
        dtcPattern = "P0131",
        pids = listOf(0x03, 0x14, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x05),
        tPreSeconds = 60,
        tPostSeconds = 30,
        thresholdRules = listOf(
            PidThresholdRule(
                id = "o2_b1s1_low_closed_loop",
                description = "O2 B1S1 < 0.08 V for > 3s while Closed Loop & Coolant > 60 °C",
                targetPid = 0x14,
                operator = ThresholdOperator.LESS_THAN,
                threshold = 0.08,
                sustainedMs = 3_000L,
                minCoolantC = 60.0,
                requireClosedLoop = true,
            ),
        ),
    )

    val GENERAL_CAN = DtcPidProfile(
        id = "general_can",
        name = "General Powertrain (CAN)",
        dtcPattern = "P0*",
        pids = listOf(0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x11, 0x42),
        tPreSeconds = 60,
        tPostSeconds = 30,
    )

    val ALL: List<DtcPidProfile> = listOf(P0131_O2_B1S1, GENERAL_CAN)
}

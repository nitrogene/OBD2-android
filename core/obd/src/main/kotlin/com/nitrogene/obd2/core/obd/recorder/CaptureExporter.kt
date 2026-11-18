package com.nitrogene.obd2.core.obd.recorder

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializes [FaultCapture] incidents to self-contained JSON (for AI / archival) and CSV (for spreadsheets).
 */
object CaptureExporter {
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(capture: FaultCapture): String = prettyJson.encodeToString(capture)

    fun fromJson(jsonText: String): FaultCapture = prettyJson.decodeFromString(jsonText)

    fun toCsv(capture: FaultCapture): String {
        val pidColumns = capture.samples
            .flatMap { it.pidValues.keys }
            .distinct()
            .sorted()

        val sb = StringBuilder()
        sb.append("# capture_id=${capture.metadata.captureId}, ")
        sb.append("captured_at=${capture.metadata.capturedAtIso}, ")
        sb.append("vin=${capture.metadata.vin ?: "UNKNOWN"}, ")
        sb.append("protocol=${capture.metadata.protocolDescription}, ")
        sb.append("trigger=${capture.faultSummary.triggerReason}, ")
        sb.append("confirmed_dtcs=${capture.faultSummary.confirmedDtcs.joinToString(";")}, ")
        sb.append("pending_dtcs=${capture.faultSummary.pendingDtcs.joinToString(";")}\n")

        val header = buildList {
            add("t_rel_ms")
            add("timestamp_ms")
            add("vbat_v")
            add("gps_lat")
            add("gps_lon")
            add("gps_alt_m")
            add("gps_kmh")
            pidColumns.forEach { pid ->
                add("pid_0x${pid.toString(16).uppercase().padStart(2, '0')}")
            }
        }
        sb.append(header.joinToString(",")).append('\n')

        for (sample in capture.samples) {
            val row = buildList {
                add(sample.tRelMs.toString())
                add(sample.timestampMs.toString())
                add(sample.vbatV?.toString().orEmpty())
                add(sample.location?.latitude?.toString().orEmpty())
                add(sample.location?.longitude?.toString().orEmpty())
                add(sample.location?.altitudeM?.toString().orEmpty())
                add(sample.location?.speedKmh?.toString().orEmpty())
                pidColumns.forEach { pid ->
                    add(sample.pidValues[pid]?.toString().orEmpty())
                }
            }
            sb.append(row.joinToString(",")).append('\n')
        }
        return sb.toString()
    }
}

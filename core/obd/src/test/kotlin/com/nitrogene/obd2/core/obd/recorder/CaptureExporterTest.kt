package com.nitrogene.obd2.core.obd.recorder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaptureExporterTest {
    @Test
    fun `round-trips FaultCapture JSON and renders CSV with metadata and GPS columns`() {
        val capture = FaultCapture(
            metadata = CaptureMetadata(
                captureId = "cap-1234",
                capturedAtIso = "2026-11-18T13:00:00Z",
                vin = "KL1SF69TJ3B000001",
                protocolDescription = "ISO 14230-4 (KWP FAST)",
                profileId = "p0131_o2_b1s1",
                tPreSeconds = 60,
                tPostSeconds = 30,
            ),
            faultSummary = FaultSummary(
                triggerReason = "New confirmed DTC(s): P0131",
                confirmedDtcs = listOf("P0131"),
                pendingDtcs = emptyList(),
                freezeFrameDtc = "P0131",
                milOn = true,
            ),
            samples = listOf(
                TelemetrySample(
                    timestampMs = 1_000L,
                    tRelMs = -5_000L,
                    vbatV = 14.1,
                    pidValues = mapOf(0x0C to 2100.0, 0x14 to 0.04),
                    location = LocationSnapshot(48.8566, 2.3522, altitudeM = 35.0, speedKmh = 50.0),
                ),
            ),
        )

        val json = CaptureExporter.toJson(capture)
        val decoded = CaptureExporter.fromJson(json)
        assertEquals(capture, decoded)

        val csv = CaptureExporter.toCsv(capture)
        assertTrue(csv.contains("# capture_id=cap-1234"))
        assertTrue(csv.contains("t_rel_ms,timestamp_ms,vbat_v,gps_lat,gps_lon,gps_alt_m,gps_kmh,pid_0x0C,pid_0x14"))
        assertTrue(csv.contains("-5000,1000,14.1,48.8566,2.3522,35.0,50.0,2100.0,0.04"))
    }
}

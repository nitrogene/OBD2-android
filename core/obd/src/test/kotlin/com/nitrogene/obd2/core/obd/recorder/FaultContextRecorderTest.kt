package com.nitrogene.obd2.core.obd.recorder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaultContextRecorderTest {
    @Test
    fun `prunes ring buffer to T_pre and completes capture after T_post on manual trigger`() {
        var nowMs = 100_000L
        val recorder = FaultContextRecorder(clockMs = { nowMs })

        recorder.arm(
            profile = DefaultProfiles.P0131_O2_B1S1,
            tPreSeconds = 15,
            tPostSeconds = 10,
            vin = "KL1SF69TJ3B000001",
            protocolDescription = "ISO 14230-4 (KWP FAST)",
        )

        // Samples at t=100s, t=110s, t=120s -> when triggered at t=120s, t=100s (>15s old) is pruned
        assertNull(recorder.recordSample(mapOf(0x0C to 900.0), vbatV = 14.1))
        nowMs = 110_000L
        assertNull(recorder.recordSample(mapOf(0x0C to 1800.0), vbatV = 14.2))
        nowMs = 120_000L
        assertNull(
            recorder.recordSample(
                mapOf(0x0C to 2200.0),
                vbatV = 14.2,
                location = LocationSnapshot(48.8566, 2.3522, altitudeM = 35.0, speedKmh = 52.0),
            ),
        )

        recorder.markManualEvent("Intermittent stumble")
        assertTrue(recorder.state.value is RecorderState.Triggered)

        nowMs = 125_000L
        assertNull(recorder.recordSample(mapOf(0x0C to 1500.0), vbatV = 14.0))

        nowMs = 130_000L
        val capture = recorder.recordSample(mapOf(0x0C to 850.0), vbatV = 13.9)

        assertNotNull(capture)
        requireNotNull(capture)
        assertEquals("KL1SF69TJ3B000001", capture.metadata.vin)
        assertEquals("Intermittent stumble", capture.faultSummary.triggerReason)
        assertEquals(4, capture.samples.size)
        assertEquals(-10_000L, capture.samples.first().tRelMs)
        assertEquals(10_000L, capture.samples.last().tRelMs)
        assertEquals(48.8566, capture.samples[1].location?.latitude)
    }

    @Test
    fun `triggers automatically when P0131 O2 B1S1 threshold is sustained over 3s`() {
        var nowMs = 10_000L
        val recorder = FaultContextRecorder(clockMs = { nowMs })
        recorder.arm(DefaultProfiles.P0131_O2_B1S1, tPreSeconds = 60, tPostSeconds = 10)

        val closedLoopLowO2 = mapOf(
            0x03 to 2.0, // Closed loop
            0x05 to 85.0, // Coolant > 60 C
            0x14 to 0.04, // O2 B1S1 < 0.08 V
        )

        recorder.recordSample(closedLoopLowO2)
        assertTrue(recorder.state.value is RecorderState.Armed)

        nowMs = 13_500L
        recorder.recordSample(closedLoopLowO2)
        val state = recorder.state.value
        assertTrue(state is RecorderState.Triggered)
    }

    @Test
    fun `triggers automatically on new confirmed DTC`() {
        var nowMs = 50_000L
        val recorder = FaultContextRecorder(clockMs = { nowMs })
        recorder.arm(DefaultProfiles.GENERAL_CAN, tPreSeconds = 30, tPostSeconds = 10)

        recorder.recordSample(mapOf(0x0C to 2000.0))
        recorder.onDtcStatusUpdated(
            confirmed = listOf("P0131"),
            pending = emptyList(),
            mil = true,
            freezeFrame = "P0131",
        )

        val state = recorder.state.value
        assertTrue(state is RecorderState.Triggered)

        nowMs = 60_000L
        val capture = recorder.recordSample(mapOf(0x0C to 800.0))
        assertNotNull(capture)
        assertEquals(listOf("P0131"), capture?.faultSummary?.confirmedDtcs)
        assertEquals("P0131", capture?.faultSummary?.freezeFrameDtc)
        assertTrue(capture?.faultSummary?.milOn == true)
    }
}

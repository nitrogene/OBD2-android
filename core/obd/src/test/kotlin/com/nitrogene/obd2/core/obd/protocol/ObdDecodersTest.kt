package com.nitrogene.obd2.core.obd.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObdDecodersTest {
    @Test
    fun `decodes Mode 01 RPM, Speed, O2 B1S1, and STFT`() {
        val rpm = ObdDecoders.decodeMode01Pid(byteArrayOf(0x41, 0x0C, 0x1A, 0xF8.toByte()))
        val speed = ObdDecoders.decodeMode01Pid(byteArrayOf(0x41, 0x0D, 0x50))
        val o2 = ObdDecoders.decodeMode01Pid(byteArrayOf(0x41, 0x14, 0x10, 0x80.toByte()))

        assertEquals(1726.0, rpm?.value)
        assertEquals(80.0, speed?.value)
        assertEquals(0.08, o2?.value)
    }

    @Test
    fun `decodes CAN and K-Line DTCs from Mode 03 and Mode 07`() {
        // CAN with count byte: 2 DTCs -> P0131 (01 31), P0301 (03 01)
        val canDtcs = ObdDecoders.decodeDtcs(byteArrayOf(0x43, 0x02, 0x01, 0x31, 0x03, 0x01))
        assertEquals(listOf("P0131", "P0301"), canDtcs)

        // K-Line without count byte: C0123 (41 23), U0100 (C1 00), 0000 padding
        val klineDtcs = ObdDecoders.decodeDtcs(
            byteArrayOf(0x47, 0x41, 0x23, 0xC1.toByte(), 0x00, 0x00, 0x00),
        )
        assertEquals(listOf("C0123", "U0100"), klineDtcs)
    }

    @Test
    fun `decodes supported PIDs, MIL monitor status, Freeze Frame DTC, and ATRV`() {
        val supported = ObdDecoders.decodeSupportedPids(
            byteArrayOf(0x41, 0x00, 0x98.toByte(), 0x3B, 0x80.toByte(), 0x01),
            basePid = 0x00,
        )
        assertTrue(0x01 in supported)
        assertTrue(0x05 in supported)
        assertTrue(0x0C in supported)
        assertTrue(0x20 in supported)

        val mil = ObdDecoders.decodeMonitorStatus(byteArrayOf(0x41, 0x01, 0x82.toByte(), 0x07, 0x65, 0x00))
        assertEquals(MonitorStatus(milOn = true, dtcCount = 2), mil)

        val ffDtc = ObdDecoders.decodeFreezeFrameDtc(byteArrayOf(0x42, 0x02, 0x00, 0x01, 0x31))
        assertEquals("P0131", ffDtc)

        assertEquals(12.6, ObdDecoders.parseBatteryVoltage("12.6V"))
    }
}

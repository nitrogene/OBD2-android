package com.nitrogene.obd2.core.obd.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ObdResponseParserTest {
    @Test
    fun `parses CAN 11-bit ATH1 compact and spaced single-frame responses`() {
        val compact = ObdResponseParser.parsePayload("7E8064100181A8001", expectedService = 0x41)
        val spaced = ObdResponseParser.parsePayload("7E8 06 41 00 18 1A 80 01", expectedService = 0x41)

        val expected = byteArrayOf(0x41, 0x00, 0x18, 0x1A, 0x80.toByte(), 0x01)
        assertArrayEquals(expected, compact)
        assertArrayEquals(expected, spaced)
    }

    @Test
    fun `parses ISO-TP multi-frame VIN response`() {
        val raw = "014\r0:490201314847\r1:434D3832363333\r2:41303034333532"
        val payload = ObdResponseParser.parsePayload(raw, expectedService = 0x49)

        assertEquals(20, payload.size)
        assertEquals("1HGCM82633A004352", ObdDecoders.decodeVin(payload))
    }

    @Test
    fun `parses K-Line 3-byte header response with trailing checksum`() {
        val payload = ObdResponseParser.parsePayload("486B1041057B2E", expectedService = 0x41)

        assertArrayEquals(byteArrayOf(0x41, 0x05, 0x7B), payload)
        assertEquals(83.0, ObdDecoders.decodeMode01Pid(payload)?.value)
    }

    @Test
    fun `throws ObdNegativeResponseException on 7F response`() {
        val ex = assertThrows(ObdNegativeResponseException::class.java) {
            ObdResponseParser.parsePayload("7E8037F2733", expectedService = 0x67)
        }
        assertEquals(0x27, ex.requestedSid)
        assertEquals(0x33, ex.nrc)
    }

    @Test
    fun `throws ObdProtocolException on NO DATA`() {
        assertThrows(ObdProtocolException::class.java) {
            ObdResponseParser.parsePayload("NO DATA", expectedService = 0x41)
        }
    }
}

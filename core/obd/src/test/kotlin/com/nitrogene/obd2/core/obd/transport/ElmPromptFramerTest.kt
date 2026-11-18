package com.nitrogene.obd2.core.obd.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ElmPromptFramerTest {
    @Test
    fun `accumulates split BLE notifications until prompt terminator`() {
        val framer = ElmPromptFramer()

        assertNull(framer.append("ELM32".encodeToByteArray()))
        assertNull(framer.append("7 v1.5".encodeToByteArray()))
        val frame = framer.append("\r\n>".encodeToByteArray())

        assertEquals("ELM327 v1.5", frame)
    }

    @Test
    fun `strips nul bytes and normalizes multi-line crlf responses`() {
        val framer = ElmPromptFramer()

        val frame = framer.append("\u00007E8064100\r\n0:490201\r\n>".encodeToByteArray())

        assertEquals("7E8064100\r0:490201", frame)
    }

    @Test
    fun `reset clears partial buffer so stale bytes do not leak`() {
        val framer = ElmPromptFramer()

        assertNull(framer.append("STALE_PARTIAL".encodeToByteArray()))
        framer.reset()
        val frame = framer.append("OK\r>".encodeToByteArray())

        assertEquals("OK", frame)
    }

    @Test
    fun `formatCommand ensures single trailing carriage return`() {
        assertEquals("ATZ\r", ElmPromptFramer.formatCommand("ATZ"))
        assertEquals("ATZ\r", ElmPromptFramer.formatCommand("ATZ\r"))
        assertEquals("0100\r", ElmPromptFramer.formatCommand("0100\r\n"))
    }
}

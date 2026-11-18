package com.nitrogene.obd2.core.obd.protocol

import com.nitrogene.obd2.core.obd.transport.ElmTransport
import com.nitrogene.obd2.core.obd.transport.TransportManager
import com.nitrogene.obd2.core.obd.transport.TransportState
import com.nitrogene.obd2.core.obd.transport.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ElmInitializerAndGuardTest {
    private class MockElmTransport : ElmTransport {
        override val type = TransportType.WIFI_TCP
        private val _state = MutableStateFlow<TransportState>(
            TransportState.Connected(TransportType.WIFI_TCP, "192.168.42.1:35000"),
        )
        override val state: StateFlow<TransportState> = _state.asStateFlow()

        val sentCommands = mutableListOf<String>()

        override suspend fun connect() {}
        override suspend fun disconnect() {}

        override suspend fun sendCommand(command: String, timeoutMs: Long): String {
            sentCommands.add(command)
            return when (command) {
                "ATZ" -> "ELM327 v1.5"
                "0100" -> "486B1041009818800000"
                "ATDP" -> "ISO 14230-4 (KWP FAST)"
                "ATRV" -> "12.4V"
                else -> "OK"
            }
        }
    }

    @Test
    fun `initializes ELM327 sequence and detects K-Line protocol`() = runTest {
        val manager = TransportManager(this)
        val mock = MockElmTransport()
        manager.connect(mock)

        val session = ElmInitializer(manager).initialize(ElmProtocol.KWP2000_FAST)

        assertEquals("ELM327 v1.5", session.elmVersion)
        assertTrue(session.isKLine)
        assertEquals(12.4, session.batteryVoltageV)
        assertTrue(0x0C in session.supportedPids)
        assertEquals(
            listOf("ATZ", "ATE0", "ATD0", "ATH1", "ATSP5", "ATM0", "ATS0", "ATAT1", "ATAL", "ATST64", "0100", "ATDP", "ATRV"),
            mock.sentCommands,
        )
        manager.disconnect()
    }

    @Test
    fun `cadence guard caps K-Line at 8 PIDs and leaves CAN uncapped`() {
        val requested = (1..12).toList()

        val klinePlan = PollingCadenceGuard.plan(requested, isKLine = true)
        assertTrue(klinePlan.kLineCapped)
        assertEquals(8, klinePlan.effectivePids.size)
        assertEquals(listOf(9, 10, 11, 12), klinePlan.droppedPids)

        val canPlan = PollingCadenceGuard.plan(requested, isKLine = false)
        assertFalse(canPlan.kLineCapped)
        assertEquals(12, canPlan.effectivePids.size)
    }

    @Test
    fun `security gate flags AT+EXT and restricted SIDs`() {
        assertTrue(DiagnosticSecurityGate.requiresExtendedConfirmation("AT+EXT=1"))
        assertTrue(DiagnosticSecurityGate.requiresExtendedConfirmation("2701"))
        assertTrue(DiagnosticSecurityGate.requiresExtendedConfirmation("1101"))
        assertTrue(DiagnosticSecurityGate.requiresExtendedConfirmation("8502"))

        assertFalse(DiagnosticSecurityGate.requiresExtendedConfirmation("010C"))
        assertFalse(DiagnosticSecurityGate.requiresExtendedConfirmation("03"))
        assertFalse(DiagnosticSecurityGate.requiresExtendedConfirmation("ATRV"))
    }
}

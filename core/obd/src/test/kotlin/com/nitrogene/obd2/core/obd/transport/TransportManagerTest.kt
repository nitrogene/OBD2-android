package com.nitrogene.obd2.core.obd.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransportManagerTest {
    private class FakeTransport(
        override val type: TransportType,
        private val endpoint: String,
    ) : ElmTransport {
        private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
        override val state: StateFlow<TransportState> = _state.asStateFlow()

        var connectCount = 0
        var disconnectCount = 0
        var lastCommand: String? = null

        override suspend fun connect() {
            connectCount++
            _state.value = TransportState.Connected(type, endpoint)
        }

        override suspend fun disconnect() {
            disconnectCount++
            _state.value = TransportState.Disconnected
        }

        override suspend fun sendCommand(command: String, timeoutMs: Long): String {
            lastCommand = command
            return "OK:$command"
        }
    }

    @Test
    fun `switching transports cleanly disconnects previous transport first`() = runTest {
        val manager = TransportManager(this)
        val ble = FakeTransport(TransportType.BLE, "OBD2-ELM327")
        val wifi = FakeTransport(TransportType.WIFI_TCP, "192.168.42.1:35000")

        manager.connect(ble)
        assertEquals(TransportType.BLE, manager.activeType)
        assertEquals(1, ble.connectCount)
        assertEquals("OK:ATZ", manager.sendCommand("ATZ"))

        manager.connect(wifi)
        assertEquals(1, ble.disconnectCount)
        assertEquals(1, wifi.connectCount)
        assertEquals(TransportType.WIFI_TCP, manager.activeType)
        assertTrue(manager.state.value is TransportState.Connected)

        manager.disconnect()
        assertEquals(1, wifi.disconnectCount)
        assertNull(manager.activeType)
        assertEquals(TransportState.Disconnected, manager.state.value)
    }

    @Test
    fun `sendCommand throws when no transport is connected`() = runTest {
        val manager = TransportManager(this)
        assertThrows(TransportDisconnectedException::class.java) {
            kotlinx.coroutines.runBlocking {
                manager.sendCommand("ATZ")
            }
        }
    }
}

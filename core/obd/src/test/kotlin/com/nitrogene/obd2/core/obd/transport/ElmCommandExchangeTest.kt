package com.nitrogene.obd2.core.obd.transport

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ElmCommandExchangeTest {
    @Test
    fun `completes command across split chunks`() = runTest {
        val exchange = ElmCommandExchange()
        var writtenPayload = ""

        val response = exchange.execute("ATZ", timeoutMs = 1_000L) { bytes ->
            writtenPayload = bytes.decodeToString()
            launch {
                delay(10)
                exchange.onBytesReceived("ELM327 ".encodeToByteArray())
                delay(10)
                exchange.onBytesReceived("v1.5\r>".encodeToByteArray())
            }
        }

        assertEquals("ATZ\r", writtenPayload)
        assertEquals("ELM327 v1.5", response)
    }

    @Test
    fun `throws TransportTimeoutException and resets partial buffer on timeout`() = runTest {
        val exchange = ElmCommandExchange()

        assertThrows(TransportTimeoutException::class.java) {
            kotlinx.coroutines.runBlocking {
                exchange.execute("0100", timeoutMs = 50L) {
                    exchange.onBytesReceived("NO_PROMPT".encodeToByteArray())
                }
            }
        }

        val recovered = exchange.execute("ATRV", timeoutMs = 500L) {
            exchange.onBytesReceived("12.6V\r>".encodeToByteArray())
        }
        assertEquals("12.6V", recovered)
    }

    @Test
    fun `throws TransportDisconnectedException when link drops mid-command`() = runTest {
        val exchange = ElmCommandExchange()

        assertThrows(TransportDisconnectedException::class.java) {
            kotlinx.coroutines.runBlocking {
                exchange.execute("010C", timeoutMs = 1_000L) {
                    launch {
                        delay(15)
                        exchange.onLinkLost("GATT disconnected")
                    }
                }
            }
        }
    }
}

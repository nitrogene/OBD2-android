package com.nitrogene.obd2.core.obd.transport

import kotlinx.coroutines.flow.StateFlow

/**
 * Unified ELM327 transport contract implemented by BLE GATT and Wi-Fi TCP clients.
 */
interface ElmTransport {
    val type: TransportType
    val state: StateFlow<TransportState>

    /**
     * Opens the transport link and prepares it to exchange `>`-framed ELM327 commands.
     */
    suspend fun connect()

    /**
     * Cleanly closes the underlying BLE GATT connection or TCP socket.
     */
    suspend fun disconnect()

    /**
     * Sends a single ELM327 command (appending `\r` if absent) and awaits the response up to `>`.
     *
     * @throws TransportTimeoutException if `>` is not received within [timeoutMs].
     * @throws TransportDisconnectedException if the transport is not connected or drops.
     */
    suspend fun sendCommand(
        command: String,
        timeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS,
    ): String

    companion object {
        const val DEFAULT_COMMAND_TIMEOUT_MS: Long = 5_000L
        const val FIRMWARE_LOCK_RELEASE_MS: Long = 5_000L
    }
}

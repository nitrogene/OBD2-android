package com.nitrogene.obd2.core.obd.transport

/**
 * Supported wireless transport types for the ESP32-S3 OBD-II dongle.
 */
enum class TransportType {
    BLE,
    WIFI_TCP,
}

/**
 * Lifecycle state of an [ElmTransport] or [TransportManager].
 */
sealed interface TransportState {
    data object Disconnected : TransportState

    data class Connecting(
        val type: TransportType,
        val endpoint: String,
    ) : TransportState

    data class Connected(
        val type: TransportType,
        val endpoint: String,
    ) : TransportState

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : TransportState
}

/**
 * Thrown when an ELM327 command does not receive the `>` prompt within the configured timeout.
 */
class TransportTimeoutException(message: String) : Exception(message)

/**
 * Thrown when a command is attempted while disconnected or the underlying link drops mid-command.
 */
class TransportDisconnectedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

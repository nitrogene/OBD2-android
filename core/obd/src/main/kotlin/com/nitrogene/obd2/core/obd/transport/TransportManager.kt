package com.nitrogene.obd2.core.obd.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Enforces the single-active-transport discipline required by the ESP32-S3 `transport_stream` lock
 * and serializes ELM327 request/response exchanges across callers.
 */
class TransportManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val lifecycleMutex = Mutex()
    private val commandMutex = Mutex()

    private var activeTransport: ElmTransport? = null
    private var stateCollectorJob: Job? = null

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    val state: StateFlow<TransportState> = _state.asStateFlow()

    val activeType: TransportType?
        get() = activeTransport?.type

    /**
     * Cleanly disconnects any existing transport before connecting [transport].
     */
    suspend fun connect(transport: ElmTransport) = lifecycleMutex.withLock {
        disconnectLocked()

        val collector = scope.launch {
            transport.state.collect { newState ->
                _state.value = newState
            }
        }
        stateCollectorJob = collector

        try {
            transport.connect()
            activeTransport = transport
            _state.value = transport.state.value
        } catch (t: Throwable) {
            collector.cancel()
            stateCollectorJob = null
            activeTransport = null
            _state.value = TransportState.Error(
                message = t.message ?: "Failed to connect ${transport.type}",
                cause = t,
            )
            throw t
        }
    }

    /**
     * Cleanly closes the active transport, releasing the firmware's `transport_stream` lock.
     */
    suspend fun disconnect() = lifecycleMutex.withLock {
        disconnectLocked()
        _state.value = TransportState.Disconnected
    }

    /**
     * Sends a single ELM327 command over the active transport, serialized via [commandMutex].
     */
    suspend fun sendCommand(
        command: String,
        timeoutMs: Long = ElmTransport.DEFAULT_COMMAND_TIMEOUT_MS,
    ): String = commandMutex.withLock {
        val current = activeTransport
            ?: throw TransportDisconnectedException("No active ELM327 transport connected")
        return current.sendCommand(command, timeoutMs)
    }

    private suspend fun disconnectLocked() {
        stateCollectorJob?.cancel()
        stateCollectorJob = null
        val current = activeTransport
        activeTransport = null
        if (current != null) {
            runCatching { current.disconnect() }
        }
    }
}

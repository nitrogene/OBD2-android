package com.nitrogene.obd2.core.obd.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Shared request/response coordinator for `>`-framed ELM327 transports.
 *
 * Ensures a single in-flight command at a time, accumulates split chunks via [ElmPromptFramer],
 * and cleans up partial buffers on timeout or link loss.
 */
class ElmCommandExchange {
    private val commandMutex = Mutex()
    private val framer = ElmPromptFramer()

    @Volatile
    private var pendingResponse: CompletableDeferred<String>? = null

    /**
     * Feeds incoming bytes from a BLE notification or TCP socket read into the framer.
     */
    fun onBytesReceived(bytes: ByteArray, length: Int = bytes.size) {
        val frame = framer.append(bytes, length) ?: return
        val pending = pendingResponse
        if (pending != null && !pending.isCompleted) {
            pending.complete(frame)
        }
    }

    /**
     * Notifies any in-flight command that the underlying transport link was lost.
     */
    fun onLinkLost(message: String = "Transport disconnected", cause: Throwable? = null) {
        framer.reset()
        val pending = pendingResponse
        pendingResponse = null
        if (pending != null && !pending.isCompleted) {
            pending.completeExceptionally(TransportDisconnectedException(message, cause))
        }
    }

    /**
     * Sends [command] using [writeAction] and awaits the `>`-terminated response within [timeoutMs].
     */
    suspend fun execute(
        command: String,
        timeoutMs: Long,
        writeAction: suspend (ByteArray) -> Unit,
    ): String = commandMutex.withLock {
        framer.reset()
        val deferred = CompletableDeferred<String>()
        pendingResponse = deferred

        val payload = ElmPromptFramer.formatCommand(command).encodeToByteArray()
        try {
            writeAction(payload)
            return withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (timeout: TimeoutCancellationException) {
            framer.reset()
            throw TransportTimeoutException(
                "Timed out after ${timeoutMs}ms waiting for '>' prompt on '$command'",
            )
        } finally {
            if (pendingResponse === deferred) {
                pendingResponse = null
            }
        }
    }
}

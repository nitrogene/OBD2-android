package com.nitrogene.obd2.transport.wifi

import com.nitrogene.obd2.core.obd.transport.ElmCommandExchange
import com.nitrogene.obd2.core.obd.transport.ElmTransport
import com.nitrogene.obd2.core.obd.transport.TransportDisconnectedException
import com.nitrogene.obd2.core.obd.transport.TransportState
import com.nitrogene.obd2.core.obd.transport.TransportType
import com.nitrogene.obd2.diag.AppDiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * Native Wi-Fi SoftAP TCP implementation of [ElmTransport] for `OBD2-Scanner` (`192.168.42.1:35000`).
 */
class WifiTcpElmTransport(
    private val networkBinder: WifiNetworkBinder,
    private val host: String = WifiConstants.DEFAULT_HOST,
    private val port: Int = WifiConstants.DEFAULT_PORT,
    private val connectTimeoutMs: Int = 5_000,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ElmTransport {

    override val type: TransportType = TransportType.WIFI_TCP

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val exchange = ElmCommandExchange()

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var outputStream: OutputStream? = null

    private var readerJob: Job? = null

    override suspend fun connect() {
        val endpoint = "$host:$port"
        _state.value = TransportState.Connecting(TransportType.WIFI_TCP, endpoint)
        AppDiagLog.log("WIFI", "Connecting TCP -> $endpoint")

        try {
            val (_, socketFactory) = networkBinder.acquireWifiSocketFactory()
            AppDiagLog.log("WIFI", "Opening bound socket to $host:$port")
            val tcpSocket = withTimeout(connectTimeoutMs.toLong()) {
                withContext(Dispatchers.IO) {
                    socketFactory.createSocket(host, port).apply {
                        tcpNoDelay = true
                    }
                }
            }
            AppDiagLog.log("WIFI", "TCP connected to $endpoint (local=${tcpSocket.localPort}, remote=${tcpSocket.port})")
            socket = tcpSocket
            outputStream = tcpSocket.getOutputStream()
            startReaderLoop(tcpSocket.getInputStream())
            _state.value = TransportState.Connected(TransportType.WIFI_TCP, endpoint)
        } catch (t: Throwable) {
            disconnect()
            _state.value = TransportState.Error(t.message ?: "Wi-Fi TCP connection failed", t)
            throw t
        }
    }

    override suspend fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        val currentSocket = socket
        socket = null
        outputStream = null
        exchange.onLinkLost("Wi-Fi TCP transport disconnected")
        if (currentSocket != null) {
            withContext(Dispatchers.IO) {
                runCatching { currentSocket.close() }
            }
        }
        networkBinder.releaseBinding()
        _state.value = TransportState.Disconnected
    }

    override suspend fun sendCommand(command: String, timeoutMs: Long): String {
        val out = outputStream
        if (socket == null || out == null || _state.value !is TransportState.Connected) {
            throw TransportDisconnectedException("Wi-Fi TCP transport is not connected")
        }
        return exchange.execute(command, timeoutMs) { payload ->
            withContext(Dispatchers.IO) {
                out.write(payload)
                out.flush()
            }
        }
    }

    private fun startReaderLoop(input: InputStream) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            try {
                while (isActive) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        exchange.onLinkLost("Wi-Fi TCP peer closed connection")
                        _state.value = TransportState.Disconnected
                        break
                    }
                    if (read > 0) {
                        exchange.onBytesReceived(buffer, read)
                    }
                }
            } catch (t: Throwable) {
                if (isActive) {
                    exchange.onLinkLost("Wi-Fi TCP read error", t)
                    _state.value = TransportState.Error(t.message ?: "Wi-Fi TCP read failed", t)
                }
            }
        }
    }
}

package com.nitrogene.obd2.ui

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitrogene.obd2.core.obd.protocol.DecodedPidValue
import com.nitrogene.obd2.core.obd.protocol.DiagnosticSecurityGate
import com.nitrogene.obd2.core.obd.protocol.ElmInitializer
import com.nitrogene.obd2.core.obd.protocol.ElmProtocol
import com.nitrogene.obd2.core.obd.protocol.ElmSessionInfo
import com.nitrogene.obd2.core.obd.protocol.ObdDecoders
import com.nitrogene.obd2.core.obd.protocol.ObdResponseParser
import com.nitrogene.obd2.core.obd.protocol.PollingCadenceGuard
import com.nitrogene.obd2.core.obd.recorder.DtcPidProfile
import com.nitrogene.obd2.core.obd.recorder.FaultCapture
import com.nitrogene.obd2.core.obd.recorder.FaultContextRecorder
import com.nitrogene.obd2.core.obd.recorder.RecorderState
import com.nitrogene.obd2.core.obd.transport.TransportManager
import com.nitrogene.obd2.core.obd.transport.TransportState
import com.nitrogene.obd2.data.CaptureRepository
import com.nitrogene.obd2.data.ProfileRepository
import com.nitrogene.obd2.data.RecorderPreferencesRepository
import com.nitrogene.obd2.data.RecorderSettings
import com.nitrogene.obd2.diag.AppDiagLog
import com.nitrogene.obd2.export.CaptureShareHelper
import com.nitrogene.obd2.service.RecorderForegroundService
import com.nitrogene.obd2.transport.ble.BleDiscoveredDevice
import com.nitrogene.obd2.transport.ble.BleElmTransport
import com.nitrogene.obd2.transport.ble.BleScanner
import com.nitrogene.obd2.transport.wifi.WifiConstants
import com.nitrogene.obd2.transport.wifi.WifiNetworkBinder
import com.nitrogene.obd2.transport.wifi.WifiTcpElmTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsoleEntry(
    val command: String,
    val response: String,
    val isError: Boolean = false,
)

@HiltViewModel
class ObdViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val transportManager: TransportManager,
    private val bleScanner: BleScanner,
    private val wifiNetworkBinder: WifiNetworkBinder,
    private val recorder: FaultContextRecorder,
    private val profileRepository: ProfileRepository,
    private val captureRepository: CaptureRepository,
    private val preferencesRepository: RecorderPreferencesRepository,
    private val shareHelper: CaptureShareHelper,
) : ViewModel() {

    val transportState: StateFlow<TransportState> = transportManager.state
    val recorderState: StateFlow<RecorderState> = recorder.state
    val profiles: StateFlow<List<DtcPidProfile>> = profileRepository.profiles
    val captures: StateFlow<List<FaultCapture>> = captureRepository.captures
    val settings: StateFlow<RecorderSettings> = preferencesRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RecorderSettings(),
    )

    private val _selectedProtocol = MutableStateFlow(ElmProtocol.AUTO)
    val selectedProtocol: StateFlow<ElmProtocol> = _selectedProtocol.asStateFlow()

    private val _sessionInfo = MutableStateFlow<ElmSessionInfo?>(null)
    val sessionInfo: StateFlow<ElmSessionInfo?> = _sessionInfo.asStateFlow()

    private val _vin = MutableStateFlow<String?>(null)
    val vin: StateFlow<String?> = _vin.asStateFlow()

    private val _batteryVoltageV = MutableStateFlow<Double?>(null)
    val batteryVoltageV: StateFlow<Double?> = _batteryVoltageV.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanningBle = MutableStateFlow(false)
    val isScanningBle: StateFlow<Boolean> = _isScanningBle.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _livePids = MutableStateFlow<Map<Int, DecodedPidValue>>(emptyMap())
    val livePids: StateFlow<Map<Int, DecodedPidValue>> = _livePids.asStateFlow()

    private val _isPollingLive = MutableStateFlow(false)
    val isPollingLive: StateFlow<Boolean> = _isPollingLive.asStateFlow()

    private val _confirmedDtcs = MutableStateFlow<List<String>>(emptyList())
    val confirmedDtcs: StateFlow<List<String>> = _confirmedDtcs.asStateFlow()

    private val _pendingDtcs = MutableStateFlow<List<String>>(emptyList())
    val pendingDtcs: StateFlow<List<String>> = _pendingDtcs.asStateFlow()

    private val _freezeFrameDtc = MutableStateFlow<String?>(null)
    val freezeFrameDtc: StateFlow<String?> = _freezeFrameDtc.asStateFlow()

    private val _extendedUnlocked = MutableStateFlow(false)
    val extendedUnlocked: StateFlow<Boolean> = _extendedUnlocked.asStateFlow()

    private val _consoleLog = MutableStateFlow<List<ConsoleEntry>>(emptyList())
    val consoleLog: StateFlow<List<ConsoleEntry>> = _consoleLog.asStateFlow()

    private var bleScanJob: Job? = null
    private var livePollJob: Job? = null

    fun selectProtocol(protocol: ElmProtocol) {
        _selectedProtocol.value = protocol
    }

    fun startBleScan(includeAll: Boolean = false) {
        bleScanJob?.cancel()
        _discoveredDevices.value = emptyList()
        _isScanningBle.value = true
        _statusMessage.value = if (includeAll) {
            "Scanning all BLE devices..."
        } else {
            "Scanning for OBD2-ELM327..."
        }
        bleScanJob = viewModelScope.launch {
            bleScanner.scanDevices(includeAll = includeAll)
                .catch { e ->
                    _isScanningBle.value = false
                    _statusMessage.value = e.message ?: "BLE scan failed"
                }
                .collect { device ->
                    val existing = _discoveredDevices.value.filterNot { it.address == device.address }
                    _discoveredDevices.value = (existing + device).sortedByDescending { it.rssi }
                }
        }
    }

    fun stopBleScan() {
        bleScanJob?.cancel()
        bleScanJob = null
        _isScanningBle.value = false
    }

    fun connectBle(device: BluetoothDevice) {
        stopBleScan()
        viewModelScope.launch {
            runCatching {
                val transport = BleElmTransport(appContext, device)
                transportManager.connect(transport)
                initializeElmSession()
            }.onFailure { e ->
                AppDiagLog.log("VM", "connectBle failed: ${e.message}")
                _statusMessage.value = "BLE connect error: ${e.message}"
            }
        }
    }

    fun connectWifi(
        host: String = WifiConstants.DEFAULT_HOST,
        port: Int = WifiConstants.DEFAULT_PORT,
    ) {
        stopBleScan()
        viewModelScope.launch {
            runCatching {
                val transport = WifiTcpElmTransport(wifiNetworkBinder, host = host, port = port)
                transportManager.connect(transport)
                initializeElmSession()
            }.onFailure { e ->
                _statusMessage.value = "Wi-Fi TCP connect error: ${e.message}"
            }
        }
    }

    fun disconnect() {
        stopLivePolling()
        viewModelScope.launch {
            transportManager.disconnect()
            _sessionInfo.value = null
            _vin.value = null
            _batteryVoltageV.value = null
            _extendedUnlocked.value = false
            _statusMessage.value = "Disconnected"
        }
    }

    private suspend fun initializeElmSession() {
        _statusMessage.value = "Initializing ELM327..."
        val info = ElmInitializer(transportManager).initialize(_selectedProtocol.value)
        _sessionInfo.value = info
        _batteryVoltageV.value = info.batteryVoltageV
        _statusMessage.value = "Connected: ${info.elmVersion} (${info.protocolDescription})"

        runCatching {
            val rawVin = transportManager.sendCommand("0902")
            val payload = ObdResponseParser.parsePayload(rawVin, expectedService = 0x49)
            _vin.value = ObdDecoders.decodeVin(payload)
        }
        refreshDtcs()
    }

    fun toggleLivePolling() {
        if (_isPollingLive.value) {
            stopLivePolling()
        } else {
            startLivePolling()
        }
    }

    private fun startLivePolling() {
        val isKLine = _sessionInfo.value?.isKLine ?: _selectedProtocol.value.isKLine
        val defaultPids = listOf(0x0C, 0x0D, 0x05, 0x14, 0x06, 0x07, 0x0B, 0x11)
        val plan = PollingCadenceGuard.plan(defaultPids, isKLine)

        _isPollingLive.value = true
        livePollJob?.cancel()
        livePollJob = viewModelScope.launch {
            while (isActive && transportState.value is TransportState.Connected) {
                for (pid in plan.effectivePids) {
                    val cmd = "01${pid.toString(16).uppercase().padStart(2, '0')}"
                    runCatching {
                        val raw = transportManager.sendCommand(cmd)
                        val payload = ObdResponseParser.parsePayload(raw, expectedService = 0x41)
                        ObdDecoders.decodeMode01Pid(payload)?.let { decoded ->
                            _livePids.value = _livePids.value + (pid to decoded)
                        }
                    }
                    delay(plan.interRequestDelayMs)
                }
                runCatching {
                    _batteryVoltageV.value = ObdDecoders.parseBatteryVoltage(
                        transportManager.sendCommand("ATRV"),
                    )
                }
            }
            _isPollingLive.value = false
        }
    }

    private fun stopLivePolling() {
        livePollJob?.cancel()
        livePollJob = null
        _isPollingLive.value = false
    }

    fun refreshDtcs() {
        viewModelScope.launch {
            runCatching {
                val raw03 = transportManager.sendCommand("03")
                val p03 = ObdResponseParser.parsePayload(raw03, expectedService = 0x43)
                _confirmedDtcs.value = ObdDecoders.decodeDtcs(p03)
            }
            runCatching {
                val raw07 = transportManager.sendCommand("07")
                val p07 = ObdResponseParser.parsePayload(raw07, expectedService = 0x47)
                _pendingDtcs.value = ObdDecoders.decodeDtcs(p07)
            }
            runCatching {
                val raw02 = transportManager.sendCommand("020200")
                val p02 = ObdResponseParser.parsePayload(raw02, expectedService = 0x42)
                _freezeFrameDtc.value = ObdDecoders.decodeFreezeFrameDtc(p02)
            }
        }
    }

    fun clearDtcs() {
        viewModelScope.launch {
            runCatching {
                transportManager.sendCommand("04")
                _confirmedDtcs.value = emptyList()
                _pendingDtcs.value = emptyList()
                _freezeFrameDtc.value = null
                _statusMessage.value = "DTCs cleared (Mode 04)"
            }.onFailure { e ->
                _statusMessage.value = "Clear DTC failed: ${e.message}"
            }
        }
    }

    fun updateRecorderSettings(
        selectedProfileId: String? = null,
        tPreSeconds: Int? = null,
        tPostSeconds: Int? = null,
        gpsContextEnabled: Boolean? = null,
    ) {
        viewModelScope.launch {
            preferencesRepository.updateSettings(
                selectedProfileId = selectedProfileId,
                tPreSeconds = tPreSeconds,
                tPostSeconds = tPostSeconds,
                gpsContextEnabled = gpsContextEnabled,
            )
        }
    }

    fun saveProfile(profile: DtcPidProfile) {
        viewModelScope.launch {
            profileRepository.saveProfile(profile)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profileId)
        }
    }

    fun toggleRecorderService() {
        if (recorderState.value is RecorderState.Idle || recorderState.value is RecorderState.Completed) {
            stopLivePolling()
            val info = _sessionInfo.value
            val intent = RecorderForegroundService.startIntent(
                context = appContext,
                isKLine = info?.isKLine ?: _selectedProtocol.value.isKLine,
                protocolDesc = info?.protocolDescription ?: _selectedProtocol.value.displayName,
                vin = _vin.value,
            )
            ContextCompat.startForegroundService(appContext, intent)
        } else {
            val stopIntent = Intent(appContext, RecorderForegroundService::class.java)
                .setAction(RecorderForegroundService.ACTION_STOP)
            appContext.startService(stopIntent)
        }
    }

    fun markManualEvent() {
        recorder.markManualEvent("Manual event marker (UI)")
    }

    fun copyCaptureJson(capture: FaultCapture) {
        shareHelper.copyJsonForAi(capture)
        _statusMessage.value = "Copied JSON capture to clipboard"
    }

    fun shareCaptureBundle(capture: FaultCapture) {
        viewModelScope.launch {
            val intent = shareHelper.createShareBundleIntent(capture)
            appContext.startActivity(intent)
        }
    }

    fun deleteCapture(captureId: String) {
        viewModelScope.launch {
            captureRepository.deleteCapture(captureId)
        }
    }

    fun sendAtCommand(
        command: String,
        confirmedExtended: Boolean = false,
    ) {
        if (DiagnosticSecurityGate.requiresExtendedConfirmation(command) && !confirmedExtended) {
            _statusMessage.value = "Command '$command' requires explicit confirmation"
            return
        }
        viewModelScope.launch {
            runCatching {
                val resp = transportManager.sendCommand(command)
                if (command.trim().uppercase().startsWith("AT+EXT=1")) {
                    _extendedUnlocked.value = true
                } else if (command.trim().uppercase().startsWith("AT+EXT=0")) {
                    _extendedUnlocked.value = false
                }
                _consoleLog.value = (_consoleLog.value + ConsoleEntry(command, resp)).takeLast(50)
            }.onFailure { e ->
                _consoleLog.value = (
                    _consoleLog.value + ConsoleEntry(
                        command = command,
                        response = e.message ?: "Error",
                        isError = true,
                    )
                    ).takeLast(50)
            }
        }
    }
}

package com.nitrogene.obd2.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nitrogene.obd2.MainActivity
import com.nitrogene.obd2.core.obd.protocol.ObdDecoders
import com.nitrogene.obd2.core.obd.protocol.ObdResponseParser
import com.nitrogene.obd2.core.obd.protocol.PollingCadenceGuard
import com.nitrogene.obd2.core.obd.recorder.DtcPidProfile
import com.nitrogene.obd2.core.obd.recorder.FaultContextRecorder
import com.nitrogene.obd2.core.obd.recorder.RecorderState
import com.nitrogene.obd2.core.obd.transport.TransportManager
import com.nitrogene.obd2.data.CaptureRepository
import com.nitrogene.obd2.data.ProfileRepository
import com.nitrogene.obd2.data.RecorderPreferencesRepository
import com.nitrogene.obd2.location.LocationContextProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service (`connectedDevice`) that keeps OBD-II polling and pre/post-fault ring
 * buffering alive when the screen is locked or another app (e.g. navigation) is in the foreground.
 */
@AndroidEntryPoint
class RecorderForegroundService : Service() {

    @Inject lateinit var transportManager: TransportManager
    @Inject lateinit var recorder: FaultContextRecorder
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var captureRepository: CaptureRepository
    @Inject lateinit var preferencesRepository: RecorderPreferencesRepository
    @Inject lateinit var locationProvider: LocationContextProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MARK_EVENT -> {
                recorder.markManualEvent("Manual event marker (notification)")
            }

            ACTION_STOP -> {
                stopRecordingAndSelf()
            }

            else -> {
                val isKLine = intent?.getBooleanExtra(EXTRA_IS_KLINE, false) ?: false
                val protocolDesc = intent?.getStringExtra(EXTRA_PROTOCOL_DESC) ?: "Unknown"
                val vin = intent?.getStringExtra(EXTRA_VIN)
                startArmedForeground(isKLine, protocolDesc, vin)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        recorder.disarm()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startArmedForeground(
        isKLine: Boolean,
        protocolDesc: String,
        vin: String?,
    ) {
        val notification = buildNotification("Armed — Buffering pre-fault telemetry")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            val prefs = preferencesRepository.settings.first()
            val profiles = profileRepository.profiles.first()
            val profile = profiles.firstOrNull { it.id == prefs.selectedProfileId }
                ?: profiles.first()

            recorder.arm(
                profile = profile,
                tPreSeconds = prefs.tPreSeconds,
                tPostSeconds = prefs.tPostSeconds,
                vin = vin,
                protocolDescription = protocolDesc,
            )

            runPollingLoop(
                profile = profile,
                isKLine = isKLine,
                gpsEnabled = prefs.gpsContextEnabled,
            )
        }
    }

    private suspend fun runPollingLoop(
        profile: DtcPidProfile,
        isKLine: Boolean,
        gpsEnabled: Boolean,
    ) {
        val plan = PollingCadenceGuard.plan(profile.pids, isKLine)
        var lastDtcCheckMs = 0L

        while (serviceScope.isActive && recorder.state.value !is RecorderState.Idle) {
            val pidValues = mutableMapOf<Int, Double>()
            for (pid in plan.effectivePids) {
                val cmd = "01${pid.toString(16).uppercase().padStart(2, '0')}"
                runCatching {
                    val raw = transportManager.sendCommand(cmd)
                    val payload = ObdResponseParser.parsePayload(raw, expectedService = 0x41)
                    ObdDecoders.decodeMode01Pid(payload)?.let { decoded ->
                        pidValues[pid] = decoded.value
                    }
                }
                delay(plan.interRequestDelayMs)
            }

            val vbatV = runCatching {
                ObdDecoders.parseBatteryVoltage(transportManager.sendCommand("ATRV"))
            }.getOrNull()

            val now = System.currentTimeMillis()
            if (now - lastDtcCheckMs >= DTC_POLL_INTERVAL_MS) {
                lastDtcCheckMs = now
                pollDtcAndMilStatus()
            }

            val location = if (gpsEnabled) locationProvider.currentSnapshot() else null
            val completed = recorder.recordSample(
                pidValues = pidValues,
                vbatV = vbatV,
                location = location,
            )

            updateNotificationForState(recorder.state.value)

            if (completed != null) {
                captureRepository.saveCapture(completed)
                stopSelf()
                break
            }
        }
    }

    private suspend fun pollDtcAndMilStatus() {
        val milOn = runCatching {
            val raw = transportManager.sendCommand("0101")
            val payload = ObdResponseParser.parsePayload(raw, expectedService = 0x41)
            ObdDecoders.decodeMonitorStatus(payload)?.milOn ?: false
        }.getOrDefault(false)

        val confirmed = runCatching {
            val raw = transportManager.sendCommand("03")
            val payload = ObdResponseParser.parsePayload(raw, expectedService = 0x43)
            ObdDecoders.decodeDtcs(payload)
        }.getOrDefault(emptyList())

        val pending = runCatching {
            val raw = transportManager.sendCommand("07")
            val payload = ObdResponseParser.parsePayload(raw, expectedService = 0x47)
            ObdDecoders.decodeDtcs(payload)
        }.getOrDefault(emptyList())

        val freezeFrame = if (confirmed.isNotEmpty() || milOn) {
            runCatching {
                val raw = transportManager.sendCommand("020200")
                val payload = ObdResponseParser.parsePayload(raw, expectedService = 0x42)
                ObdDecoders.decodeFreezeFrameDtc(payload)
            }.getOrNull()
        } else {
            null
        }

        recorder.onDtcStatusUpdated(
            confirmed = confirmed,
            pending = pending,
            mil = milOn,
            freezeFrame = freezeFrame,
        )
    }

    private fun stopRecordingAndSelf() {
        pollingJob?.cancel()
        recorder.disarm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotificationForState(state: RecorderState) {
        val text = when (state) {
            is RecorderState.Armed ->
                "Armed (${state.profile.name}) — ${state.bufferedSamples} samples buffered"
            is RecorderState.Triggered ->
                "Triggered: ${state.reason} (${state.remainingPostMs / 1000}s left)"
            is RecorderState.Completed -> "Capture saved"
            is RecorderState.Idle -> "Idle"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val markIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecorderForegroundService::class.java).setAction(ACTION_MARK_EVENT),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, RecorderForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OBD2 Fault Context Recorder")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Mark Event", markIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Fault Context Recorder",
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "obd2_recorder"
        private const val NOTIFICATION_ID = 4201
        private const val DTC_POLL_INTERVAL_MS = 2_500L

        const val ACTION_START = "com.nitrogene.obd2.action.START_RECORDER"
        const val ACTION_MARK_EVENT = "com.nitrogene.obd2.action.MARK_EVENT"
        const val ACTION_STOP = "com.nitrogene.obd2.action.STOP_RECORDER"

        const val EXTRA_IS_KLINE = "extra_is_kline"
        const val EXTRA_PROTOCOL_DESC = "extra_protocol_desc"
        const val EXTRA_VIN = "extra_vin"

        fun startIntent(
            context: Context,
            isKLine: Boolean,
            protocolDesc: String,
            vin: String?,
        ): Intent = Intent(context, RecorderForegroundService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_IS_KLINE, isKLine)
            putExtra(EXTRA_PROTOCOL_DESC, protocolDesc)
            putExtra(EXTRA_VIN, vin)
        }
    }
}

package com.nitrogene.obd2.core.obd.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

sealed interface RecorderState {
    data object Idle : RecorderState

    data class Armed(
        val profile: DtcPidProfile,
        val bufferedSamples: Int,
    ) : RecorderState

    data class Triggered(
        val profile: DtcPidProfile,
        val reason: String,
        val remainingPostMs: Long,
    ) : RecorderState

    data class Completed(
        val capture: FaultCapture,
    ) : RecorderState
}

/**
 * Pure-Kotlin pre/post-fault rolling ring buffer and capture state machine.
 */
class FaultContextRecorder(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val ringBuffer = ArrayDeque<TelemetrySample>()
    private val postSamples = mutableListOf<TelemetrySample>()
    private val ruleStartTimes = mutableMapOf<String, Long>()

    private var activeProfile: DtcPidProfile? = null
    private var tPreMs: Long = 60_000L
    private var tPostMs: Long = 30_000L
    private var triggerTimeMs: Long? = null
    private var triggerReason: String = ""
    private var vin: String? = null
    private var protocolDescription: String = "Unknown"
    private var confirmedDtcs: List<String> = emptyList()
    private var pendingDtcs: List<String> = emptyList()
    private var freezeFrameDtc: String? = null
    private var milOn: Boolean = false

    private var baselineConfirmedDtcs: Set<String> = emptySet()
    private var baselinePendingDtcs: Set<String> = emptySet()
    private var baselineMilOn: Boolean = false

    @Synchronized
    fun arm(
        profile: DtcPidProfile,
        tPreSeconds: Int = profile.tPreSeconds,
        tPostSeconds: Int = profile.tPostSeconds,
        vin: String? = null,
        protocolDescription: String = "Unknown",
        initialConfirmedDtcs: List<String> = emptyList(),
        initialPendingDtcs: List<String> = emptyList(),
        initialMilOn: Boolean = false,
    ) {
        ringBuffer.clear()
        postSamples.clear()
        ruleStartTimes.clear()

        activeProfile = profile
        tPreMs = tPreSeconds.coerceIn(15, 300) * 1_000L
        tPostMs = tPostSeconds.coerceIn(10, 120) * 1_000L
        triggerTimeMs = null
        triggerReason = ""
        this.vin = vin
        this.protocolDescription = protocolDescription

        baselineConfirmedDtcs = initialConfirmedDtcs.toSet()
        baselinePendingDtcs = initialPendingDtcs.toSet()
        baselineMilOn = initialMilOn
        confirmedDtcs = initialConfirmedDtcs
        pendingDtcs = initialPendingDtcs
        freezeFrameDtc = null
        milOn = initialMilOn

        _state.value = RecorderState.Armed(profile, 0)
    }

    @Synchronized
    fun disarm() {
        ringBuffer.clear()
        postSamples.clear()
        ruleStartTimes.clear()
        activeProfile = null
        triggerTimeMs = null
        _state.value = RecorderState.Idle
    }

    /**
     * Records a telemetry sample into the rolling ring buffer (if armed) or post-fault window
     * (if triggered), evaluating any configured [PidThresholdRule]s.
     *
     * Returns the completed [FaultCapture] if the post-fault window just finished, else `null`.
     */
    @Synchronized
    fun recordSample(
        pidValues: Map<Int, Double>,
        vbatV: Double? = null,
        location: LocationSnapshot? = null,
    ): FaultCapture? {
        val profile = activeProfile ?: return null
        val now = clockMs()
        val sample = TelemetrySample(
            timestampMs = now,
            vbatV = vbatV,
            pidValues = pidValues,
            location = location,
        )

        val currentTrigger = triggerTimeMs
        if (currentTrigger == null) {
            ringBuffer.addLast(sample)
            prunePreBuffer(now)
            _state.value = RecorderState.Armed(profile, ringBuffer.size)

            val ruleTriggered = evaluateThresholdRules(profile, pidValues, now)
            if (ruleTriggered != null) {
                triggerInternal(ruleTriggered, now)
            }
            return null
        }

        postSamples.add(sample)
        val elapsed = now - currentTrigger
        if (elapsed >= tPostMs) {
            return finalizeCapture(profile, currentTrigger)
        }

        _state.value = RecorderState.Triggered(
            profile = profile,
            reason = triggerReason,
            remainingPostMs = (tPostMs - elapsed).coerceAtLeast(0L),
        )
        return null
    }

    /**
     * Updates DTC / MIL status and triggers a capture if a new DTC or MIL activation is observed.
     */
    @Synchronized
    fun onDtcStatusUpdated(
        confirmed: List<String>,
        pending: List<String>,
        mil: Boolean,
        freezeFrame: String? = null,
    ) {
        val profile = activeProfile ?: return
        confirmedDtcs = confirmed
        pendingDtcs = pending
        milOn = mil
        if (freezeFrame != null) {
            freezeFrameDtc = freezeFrame
        }

        if (triggerTimeMs != null) return

        val newConfirmed = confirmed.filterNot { it in baselineConfirmedDtcs }
        val newPending = pending.filterNot { it in baselinePendingDtcs }
        val milRising = mil && !baselineMilOn

        val reason = when {
            newConfirmed.isNotEmpty() -> "New confirmed DTC(s): ${newConfirmed.joinToString()}"
            newPending.isNotEmpty() -> "New pending DTC(s): ${newPending.joinToString()}"
            milRising -> "MIL activated"
            else -> null
        }

        if (reason != null) {
            triggerInternal(reason, clockMs())
            _state.value = RecorderState.Triggered(profile, reason, tPostMs)
        }
    }

    /**
     * Manually triggers a fault-context capture ("Capture Now / Mark Event").
     */
    @Synchronized
    fun markManualEvent(reason: String = "Manual event marker") {
        val profile = activeProfile ?: return
        if (triggerTimeMs != null) return
        triggerInternal(reason, clockMs())
        _state.value = RecorderState.Triggered(profile, reason, tPostMs)
    }

    private fun triggerInternal(reason: String, now: Long) {
        triggerTimeMs = now
        triggerReason = reason
        postSamples.clear()
        val profile = activeProfile ?: return
        _state.value = RecorderState.Triggered(profile, reason, tPostMs)
    }

    private fun prunePreBuffer(now: Long) {
        val cutoff = now - tPreMs
        while (ringBuffer.isNotEmpty() && ringBuffer.first().timestampMs < cutoff) {
            ringBuffer.removeFirst()
        }
    }

    private fun evaluateThresholdRules(
        profile: DtcPidProfile,
        pidValues: Map<Int, Double>,
        now: Long,
    ): String? {
        for (rule in profile.thresholdRules) {
            val value = pidValues[rule.targetPid]
            val coolantOk = rule.minCoolantC == null ||
                (pidValues[0x05]?.let { it >= rule.minCoolantC } == true)
            // Mode 01 PID 0x03: 2 == Closed loop, using oxygen sensor feedback
            val closedLoopOk = !rule.requireClosedLoop || pidValues[0x03]?.toInt() == 2

            val conditionMet = value != null && coolantOk && closedLoopOk && when (rule.operator) {
                ThresholdOperator.LESS_THAN -> value < rule.threshold
                ThresholdOperator.GREATER_THAN -> value > rule.threshold
            }

            if (!conditionMet) {
                ruleStartTimes.remove(rule.id)
                continue
            }

            val start = ruleStartTimes.getOrPut(rule.id) { now }
            if (now - start >= rule.sustainedMs) {
                return "Threshold rule '${rule.description}'"
            }
        }
        return null
    }

    private fun finalizeCapture(profile: DtcPidProfile, triggerMs: Long): FaultCapture {
        val allSamples = (ringBuffer.toList() + postSamples).map { sample ->
            sample.copy(tRelMs = sample.timestampMs - triggerMs)
        }
        val capture = FaultCapture(
            metadata = CaptureMetadata(
                captureId = UUID.randomUUID().toString(),
                capturedAtIso = Instant.ofEpochMilli(triggerMs).toString(),
                vin = vin,
                protocolDescription = protocolDescription,
                profileId = profile.id,
                tPreSeconds = (tPreMs / 1_000L).toInt(),
                tPostSeconds = (tPostMs / 1_000L).toInt(),
            ),
            faultSummary = FaultSummary(
                triggerReason = triggerReason,
                confirmedDtcs = confirmedDtcs,
                pendingDtcs = pendingDtcs,
                freezeFrameDtc = freezeFrameDtc,
                milOn = milOn,
            ),
            samples = allSamples,
        )
        ringBuffer.clear()
        postSamples.clear()
        ruleStartTimes.clear()
        activeProfile = null
        triggerTimeMs = null
        _state.value = RecorderState.Completed(capture)
        return capture
    }
}

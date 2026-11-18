package com.nitrogene.obd2.core.obd.protocol

/**
 * Decoded OBD-II Mode `01` / `02` / `03` / `07` / `09` and `ATRV` values.
 */
object ObdDecoders {
    /**
     * Parses `ATRV` output (e.g. `"12.6V"` -> `12.6`), or returns `null` if unparseable.
     */
    fun parseBatteryVoltage(atrvResponse: String): Double? {
        val match = Regex("(\\d+(?:\\.\\d+)?)").find(atrvResponse.trim()) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    /**
     * Decodes supported PIDs from `0100`, `0120`, `0140` (`41 <basePid> <A> <B> <C> <D>`).
     */
    fun decodeSupportedPids(payload: ByteArray, basePid: Int): Set<Int> {
        if (payload.size < 6) return emptySet()
        val a = payload[2].toInt() and 0xFF
        val b = payload[3].toInt() and 0xFF
        val c = payload[4].toInt() and 0xFF
        val d = payload[5].toInt() and 0xFF
        val mask = (a shl 24) or (b shl 16) or (c shl 8) or d

        val result = mutableSetOf<Int>()
        for (bit in 0 until 32) {
            if (((mask ushr (31 - bit)) and 1) == 1) {
                result.add(basePid + bit + 1)
            }
        }
        return result
    }

    /**
     * Decodes `Mode 01` PID engineering value (`payload = [0x41, pid, A, ...]`).
     * Returns `null` if the PID is not yet supported by the decoder or payload is too short.
     */
    fun decodeMode01Pid(payload: ByteArray): DecodedPidValue? {
        if (payload.size < 3 || (payload[0].toInt() and 0xFF) != 0x41) return null
        val pid = payload[1].toInt() and 0xFF
        val a = payload[2].toInt() and 0xFF
        val b = if (payload.size >= 4) payload[3].toInt() and 0xFF else 0

        return when (pid) {
            0x03 -> DecodedPidValue(pid, "Fuel System Status", a.toDouble(), "state")
            0x04 -> DecodedPidValue(pid, "Calculated Engine Load", a * 100.0 / 255.0, "%")
            0x05 -> DecodedPidValue(pid, "Coolant Temperature", (a - 40).toDouble(), "°C")
            0x06 -> DecodedPidValue(pid, "STFT Bank 1", (a - 128) * 100.0 / 128.0, "%")
            0x07 -> DecodedPidValue(pid, "LTFT Bank 1", (a - 128) * 100.0 / 128.0, "%")
            0x0B -> DecodedPidValue(pid, "Intake MAP", a.toDouble(), "kPa")
            0x0C -> {
                if (payload.size < 4) return null
                DecodedPidValue(pid, "Engine RPM", ((a * 256) + b) / 4.0, "rpm")
            }
            0x0D -> DecodedPidValue(pid, "Vehicle Speed", a.toDouble(), "km/h")
            0x0E -> DecodedPidValue(pid, "Timing Advance", a / 2.0 - 64.0, "°")
            0x0F -> DecodedPidValue(pid, "Intake Air Temp", (a - 40).toDouble(), "°C")
            0x10 -> {
                if (payload.size < 4) return null
                DecodedPidValue(pid, "MAF Air Flow", ((a * 256) + b) / 100.0, "g/s")
            }
            0x11 -> DecodedPidValue(pid, "Throttle Position", a * 100.0 / 255.0, "%")
            0x14 -> DecodedPidValue(pid, "O2 B1S1 Voltage", a / 200.0, "V")
            0x15 -> DecodedPidValue(pid, "O2 B1S2 Voltage", a / 200.0, "V")
            0x2F -> DecodedPidValue(pid, "Fuel Tank Level", a * 100.0 / 255.0, "%")
            0x42 -> {
                if (payload.size < 4) return null
                DecodedPidValue(pid, "Control Module Voltage", ((a * 256) + b) / 1000.0, "V")
            }
            else -> null
        }
    }

    /**
     * Decodes MIL status and DTC count from `0101` (`41 01 <A> <B> <C> <D>`).
     */
    fun decodeMonitorStatus(payload: ByteArray): MonitorStatus? {
        if (payload.size < 4 || (payload[0].toInt() and 0xFF) != 0x41 ||
            (payload[1].toInt() and 0xFF) != 0x01
        ) {
            return null
        }
        val a = payload[2].toInt() and 0xFF
        val milOn = (a and 0x80) != 0
        val dtcCount = a and 0x7F
        return MonitorStatus(milOn = milOn, dtcCount = dtcCount)
    }

    /**
     * Decodes confirmed (`0x43`) or pending (`0x47`) DTCs from an OBD-II payload.
     *
     * Handles both CAN payloads (which include a DTC count byte: `[0x43, count, hi, lo, ...]`)
     * and K-Line payloads (`[0x43, hi, lo, ...]`).
     */
    fun decodeDtcs(payload: ByteArray): List<String> {
        if (payload.size < 3) return emptyList()
        val dataBytes = payload.size - 1
        val startIdx = if (dataBytes % 2 == 1) 2 else 1

        val dtcs = mutableListOf<String>()
        var i = startIdx
        while (i + 1 < payload.size) {
            val hi = payload[i].toInt() and 0xFF
            val lo = payload[i + 1].toInt() and 0xFF
            if (hi != 0 || lo != 0) {
                dtcs.add(formatDtc(hi, lo))
            }
            i += 2
        }
        return dtcs
    }

    /**
     * Decodes `0202` Freeze Frame DTC (`42 02 <frame> <hi> <lo>`).
     */
    fun decodeFreezeFrameDtc(payload: ByteArray): String? {
        if (payload.size < 5 || (payload[0].toInt() and 0xFF) != 0x42 ||
            (payload[1].toInt() and 0xFF) != 0x02
        ) {
            return null
        }
        val hi = payload[3].toInt() and 0xFF
        val lo = payload[4].toInt() and 0xFF
        if (hi == 0 && lo == 0) return null
        return formatDtc(hi, lo)
    }

    /**
     * Decodes `0902` VIN (`49 02 <NODI> <17 ASCII bytes>`).
     */
    fun decodeVin(payload: ByteArray): String? {
        if (payload.size < 4 || (payload[0].toInt() and 0xFF) != 0x49 ||
            (payload[1].toInt() and 0xFF) != 0x02
        ) {
            return null
        }
        val asciiBytes = payload.copyOfRange(3, payload.size)
            .filter { it in 0x20..0x7E }
            .toByteArray()
        val vin = asciiBytes.decodeToString().trim()
        return vin.ifEmpty { null }
    }

    private fun formatDtc(hi: Int, lo: Int): String {
        val prefix = when ((hi ushr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val d1 = (hi ushr 4) and 0x03
        val d2 = hi and 0x0F
        val d3 = (lo ushr 4) and 0x0F
        val d4 = lo and 0x0F
        return "$prefix$d1${d2.toString(16).uppercase()}${d3.toString(16).uppercase()}${d4.toString(16).uppercase()}"
    }
}

data class DecodedPidValue(
    val pid: Int,
    val name: String,
    val value: Double,
    val unit: String,
)

data class MonitorStatus(
    val milOn: Boolean,
    val dtcCount: Int,
)

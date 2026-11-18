package com.nitrogene.obd2.core.obd.protocol

/**
 * Parses raw ELM327 responses into clean OBD-II / KWP2000 byte payloads.
 *
 * Supports:
 * - CAN single-frame responses with `ATH1` (`7E8064100181A8001` or `7E8 06 41 00 18 1A 80 01`)
 * - ISO-TP multi-frame responses (`014\r0:490201...\r1:...\r2:...`, with or without CAN header)
 * - Unheaded responses (`ATH0`, e.g. `4100181A8001`)
 * - K-Line / KWP2000 3-byte headers (`84F1104100...` / `486B104100...`)
 */
object ObdResponseParser {
    private val ERROR_TOKENS = listOf(
        "NO DATA",
        "UNABLE TO CONNECT",
        "BUS INIT",
        "CAN ERROR",
        "STOPPED",
        "BUFFER FULL",
        "?",
    )

    /**
     * Parses a raw ELM327 response for an expected positive response service byte [expectedService]
     * (e.g. `0x41` for Mode `01`, `0x43` for Mode `03`, `0x49` for Mode `09`).
     *
     * @throws ObdProtocolException if the ECU returned an error token or negative response (`7F`).
     */
    fun parsePayload(rawResponse: String, expectedService: Int): ByteArray {
        val lines = rawResponse
            .replace("\r\n", "\r")
            .split('\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("SEARCHING", ignoreCase = true) }

        if (lines.isEmpty()) {
            throw ObdProtocolException("Empty response for service 0x${expectedService.toString(16)}")
        }

        for (line in lines) {
            val upper = line.uppercase()
            val error = ERROR_TOKENS.firstOrNull { upper.contains(it) }
            if (error != null) {
                throw ObdProtocolException("ECU/ELM error: $line")
            }
        }

        // Check for ISO-TP multi-frame (`0:...`, `1:...` or `<CAN_ID> 0:...`)
        if (lines.any { isIsoTpIndexedLine(it) }) {
            return parseIsoTpMultiFrame(lines, expectedService)
        }

        // Single-frame or K-Line response(s): pick the first line matching expectedService
        for (line in lines) {
            val hex = stripSpacesAndOptionalHeader(line, expectedService) ?: continue
            if (hex.size >= 3 && (hex[0].toInt() and 0xFF) == 0x7F) {
                val reqSid = hex[1].toInt() and 0xFF
                val nrc = hex[2].toInt() and 0xFF
                throw ObdNegativeResponseException(reqSid, nrc)
            }
            if (hex.isNotEmpty() && (hex[0].toInt() and 0xFF) == expectedService) {
                return hex
            }
        }

        throw ObdProtocolException(
            "Expected service 0x${expectedService.toString(16)} not found in '$rawResponse'",
        )
    }

    private fun isIsoTpIndexedLine(line: String): Boolean {
        val compact = line.replace(" ", "")
        return Regex("^([0-9A-Fa-f]{3}|[0-9A-Fa-f]{8})?[0-9A-Fa-f]:.*").matches(compact)
    }

    private fun parseIsoTpMultiFrame(lines: List<String>, expectedService: Int): ByteArray {
        val indexedFrames = mutableListOf<Pair<Int, ByteArray>>()
        var declaredLength: Int? = null

        for (line in lines) {
            val compact = line.replace(" ", "")
            val colonIdx = compact.indexOf(':')
            if (colonIdx < 0) {
                // Might be the ISO-TP total length line, e.g. "014" or "7E8014"
                val hexOnly = compact.takeIf { it.length in 3..4 && it.all(Char::isHexDigit) }
                if (hexOnly != null) {
                    declaredLength = hexOnly.toInt(16)
                }
                continue
            }

            val prefix = compact.substring(0, colonIdx)
            val seqChar = prefix.lastOrNull() ?: continue
            val seq = seqChar.digitToIntOrNull(16) ?: continue
            val dataHex = compact.substring(colonIdx + 1)
            indexedFrames.add(seq to hexToBytes(dataHex))
        }

        val combined = indexedFrames
            .sortedBy { it.first }
            .flatMap { it.second.toList() }
            .toByteArray()

        val trimmed = if (declaredLength != null && declaredLength in 1..combined.size) {
            combined.copyOfRange(0, declaredLength)
        } else {
            combined
        }

        if (trimmed.isEmpty() || (trimmed[0].toInt() and 0xFF) != expectedService) {
            throw ObdProtocolException("ISO-TP payload did not start with 0x${expectedService.toString(16)}")
        }
        return trimmed
    }

    private fun stripSpacesAndOptionalHeader(line: String, expectedService: Int): ByteArray? {
        val compact = line.replace(" ", "").uppercase()
        if (compact.length < 2 || !compact.all(Char::isHexDigit)) {
            return null
        }

        // 1. 11-bit CAN `ATH1` single-frame: <3-hex CAN ID><2-hex PCI len><payload...>
        if (compact.length >= 7 && compact.length % 2 == 1) {
            val pciLen = compact.substring(3, 5).toIntOrNull(16)
            if (pciLen != null && pciLen in 1..7 && compact.length >= 5 + pciLen * 2) {
                val payloadHex = compact.substring(5, 5 + pciLen * 2)
                val bytes = hexToBytes(payloadHex)
                val first = bytes[0].toInt() and 0xFF
                if (first == expectedService || first == 0x7F) {
                    return bytes
                }
            }
        }

        if (compact.length % 2 != 0) return null
        val bytes = hexToBytes(compact)

        // 2. Unheaded (`ATH0`): starts directly with expectedService or 0x7F
        val first = bytes[0].toInt() and 0xFF
        if (first == expectedService || first == 0x7F) {
            return bytes
        }

        // 3. 29-bit CAN `ATH1` single-frame: <4-byte CAN ID><1-byte PCI len><payload...>
        if (bytes.size >= 6) {
            val pciLen = bytes[4].toInt() and 0xFF
            if (pciLen in 1..7 && bytes.size >= 5 + pciLen) {
                val sid = bytes[5].toInt() and 0xFF
                if (sid == expectedService || sid == 0x7F) {
                    return bytes.copyOfRange(5, 5 + pciLen)
                }
            }
        }

        // 4. K-Line / KWP2000 3-byte header (`48 6B 10 <payload> [<csum>]` or `8x F1 10 <payload> [<csum>]`)
        if (bytes.size >= 5) {
            val sid = bytes[3].toInt() and 0xFF
            if (sid == expectedService || sid == 0x7F) {
                // Strip 3-byte header and trailing checksum byte when ATH1 is active on K-Line
                return bytes.copyOfRange(3, bytes.size - 1)
            }
        }

        return null
    }

    internal fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        require(clean.length % 2 == 0) { "Odd hex length: ${clean.length}" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * Thrown when an ELM327 or ECU response cannot be parsed or indicates a bus/protocol error.
 */
open class ObdProtocolException(message: String) : Exception(message)

/**
 * Thrown when the ECU responds with UDS/KWP2000 negative response (`7F <SID> <NRC>`).
 */
class ObdNegativeResponseException(
    val requestedSid: Int,
    val nrc: Int,
) : ObdProtocolException(
    "ECU negative response: SID=0x${requestedSid.toString(16).uppercase()} " +
        "NRC=0x${nrc.toString(16).uppercase()}",
)

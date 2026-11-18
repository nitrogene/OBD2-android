package com.nitrogene.obd2.core.obd.transport

/**
 * Accumulates incoming ELM327 byte/ASCII chunks until the `>` prompt terminator is observed.
 *
 * Designed to be reused across split BLE GATT notifications and partial TCP socket reads.
 */
class ElmPromptFramer {
    private val buffer = StringBuilder()

    /**
     * Appends raw bytes (ASCII/UTF-8) and returns a completed frame if `>` was received,
     * or `null` if more chunks are still needed.
     */
    @Synchronized
    fun append(bytes: ByteArray, length: Int = bytes.size): String? {
        if (length <= 0) return null
        val text = bytes.decodeToString(startIndex = 0, endIndex = length)
        return append(text)
    }

    /**
     * Appends a text chunk and returns a completed frame if `>` was received,
     * or `null` if more chunks are still needed.
     */
    @Synchronized
    fun append(chunk: String): String? {
        for (ch in chunk) {
            if (ch == '\u0000') continue
            if (ch == '>') {
                val frame = normalizeFrame(buffer.toString())
                buffer.clear()
                return frame
            }
            buffer.append(ch)
        }
        return null
    }

    /**
     * Discards any partial buffered data (e.g. on timeout or disconnect).
     */
    @Synchronized
    fun reset() {
        buffer.clear()
    }

    companion object {
        /**
         * Formats an AT or OBD-II command with the required trailing `\r` (`0x0D`).
         */
        fun formatCommand(command: String): String {
            val trimmed = command.trimEnd('\r', '\n')
            return "$trimmed\r"
        }

        private fun normalizeFrame(raw: String): String {
            return raw
                .replace("\r\n", "\r")
                .replace('\n', '\r')
                .trim('\r', ' ')
        }
    }
}

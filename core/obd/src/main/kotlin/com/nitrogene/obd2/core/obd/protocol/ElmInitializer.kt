package com.nitrogene.obd2.core.obd.protocol

import com.nitrogene.obd2.core.obd.transport.TransportManager

/**
 * Executes the recommended ELM327 v1.5 initialization sequence from `OBD2-firmware/android.md`.
 */
class ElmInitializer(
    private val transportManager: TransportManager,
) {
    /**
     * Resets the ELM327 adapter, configures framing, selects [protocol], probes supported PIDs
     * (`0100`, plus `0120`/`0140` when advertised), and reads `ATDP` + `ATRV`.
     */
    suspend fun initialize(protocol: ElmProtocol = ElmProtocol.AUTO): ElmSessionInfo {
        val atzResponse = transportManager.sendCommand("ATZ")
        val elmVersion = atzResponse
            .split('\r')
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            ?: "ELM327"

        val configCommands = listOf(
            "ATE0",
            "ATD0",
            "ATH1",
            "ATSP${protocol.spCode}",
            "ATM0",
            "ATS0",
            "ATAT1",
            "ATAL",
            "ATST64",
        )
        for (cmd in configCommands) {
            transportManager.sendCommand(cmd)
        }

        val supportedPids = mutableSetOf<Int>()
        val pid00Raw = transportManager.sendCommand("0100")
        val pid00Payload = ObdResponseParser.parsePayload(pid00Raw, expectedService = 0x41)
        supportedPids.addAll(ObdDecoders.decodeSupportedPids(pid00Payload, basePid = 0x00))

        if (0x20 in supportedPids) {
            runCatching {
                val pid20Raw = transportManager.sendCommand("0120")
                val pid20Payload = ObdResponseParser.parsePayload(pid20Raw, expectedService = 0x41)
                supportedPids.addAll(ObdDecoders.decodeSupportedPids(pid20Payload, basePid = 0x20))
            }
        }

        if (0x40 in supportedPids) {
            runCatching {
                val pid40Raw = transportManager.sendCommand("0140")
                val pid40Payload = ObdResponseParser.parsePayload(pid40Raw, expectedService = 0x41)
                supportedPids.addAll(ObdDecoders.decodeSupportedPids(pid40Payload, basePid = 0x40))
            }
        }

        val atdp = transportManager.sendCommand("ATDP").trim()
        val atrv = transportManager.sendCommand("ATRV")
        val isKLine = protocol.isKLine || ElmProtocol.isKLineDescription(atdp)

        return ElmSessionInfo(
            elmVersion = elmVersion,
            protocolDescription = atdp,
            isKLine = isKLine,
            batteryVoltageV = ObdDecoders.parseBatteryVoltage(atrv),
            supportedPids = supportedPids,
        )
    }
}

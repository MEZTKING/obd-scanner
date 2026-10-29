package dev.obdscanner.core.obd

import dev.obdscanner.core.elm.Elm327
import dev.obdscanner.core.elm.ElmException
import dev.obdscanner.core.elm.Frames

data class AdapterInfo(val version: String, val protocol: String, val isCan: Boolean)

/** OBD-II (SAE J1979) services on top of [Elm327]. */
class ObdClient(private val elm: Elm327) {
    private var isCan = true
    private var batching = true
    private var responseCount = true

    /** Opens the link, configures the adapter for speed and detects the vehicle protocol. */
    suspend fun connect(): AdapterInfo {
        elm.open()
        val version = elm.reset()
        elm.setup("ATE0")   // no echo
        elm.setup("ATL0")   // no line feeds
        elm.setup("ATS0")   // no spaces: fewer bytes per frame
        elm.setup("ATH0")   // no headers
        elm.setup("ATAT1")  // adaptive timing
        elm.setup("ATSP0")  // automatic protocol
        elm.command("0100", timeoutMs = 15_000) // triggers protocol search
        val protocol = elm.command("ATDPN").firstOrNull().orEmpty().removePrefix("A")
        isCan = protocol in setOf("6", "7", "8", "9", "A", "B", "C")
        return AdapterInfo(version, protocol, isCan)
    }

    suspend fun disconnect() = elm.close()

    /** All Mode 01 PIDs the ECU says it supports, by walking the 00/20/40/... bitmask chain. */
    suspend fun supportedPids(): Set<Int> {
        val supported = mutableSetOf<Int>()
        var base = 0x00
        while (base <= 0xC0) {
            val data = request(0x01, base) ?: break
            supported += decodeSupportMask(base, data)
            if ((base + 0x20) !in supported) break
            base += 0x20
        }
        return supported
    }

    /** Reads [pids] once. Uses multi-PID requests where the bus allows it. Missing PIDs are omitted. */
    suspend fun read(pids: List<Pid>): Map<Pid, Double> {
        val out = LinkedHashMap<Pid, Double>()
        if (batching && isCan && pids.size > 1) {
            try {
                for (chunk in pids.chunked(MAX_BATCH)) out += readBatch(chunk)
                return out
            } catch (e: ElmException) {
                batching = false // this ECU does not do multi-PID requests; fall back permanently
                out.clear()
            }
        }
        for (pid in pids) {
            val data = request(0x01, pid.id) ?: continue
            if (data.size >= pid.length) out[pid] = pid.decode(data.toUnsigned())
        }
        return out
    }

    suspend fun readDtcs(kind: DtcKind): List<Dtc> {
        val payload = try {
            Frames.payloads(elm.command(kind.request)).firstOrNull { it.firstOrNull() == kind.responseService.toByte() }
        } catch (e: ElmException.NoData) {
            null
        } ?: return emptyList()
        return DtcDecoder.decodeAll(payload.copyOfRange(1, payload.size), skipCountByte = isCan)
            .distinct()
            .map { Dtc(it, DtcDatabase.describe(it)) }
    }

    /** Clears stored codes and freeze frame (Mode 04). Engine should be off, ignition on. */
    suspend fun clearDtcs(): Boolean {
        val payloads = Frames.payloads(elm.command("04"))
        return payloads.any { it.firstOrNull() == 0x44.toByte() }
    }

    /** Mode 09 PID 02. Null if the ECU does not provide it. */
    suspend fun readVin(): String? {
        val payload = try {
            Frames.payloads(elm.command("0902")).firstOrNull { it.size > 3 && it[0] == 0x49.toByte() && it[1] == 0x02.toByte() }
        } catch (e: ElmException.NoData) {
            null
        } ?: return null
        val vin = payload.drop(3).map { (it.toInt() and 0xFF).toChar() }.filter { it in ' '..'~' }.joinToString("").trim()
        return vin.ifEmpty { null }
    }

    private suspend fun readBatch(pids: List<Pid>): Map<Pid, Double> {
        val cmd = "01" + pids.joinToString("") { it.hex }
        // NO DATA to a multi-PID request is ambiguous; treat it as "batching unsupported" so the caller falls back.
        val payload = singleResponse(cmd, expectedService = 0x41) ?: throw ElmException.NoData()
        val out = LinkedHashMap<Pid, Double>()
        var i = 1
        while (i < payload.size) {
            val pid = Pids.byId(payload[i].toInt() and 0xFF) ?: break
            if (i + 1 + pid.length > payload.size) break
            val data = payload.copyOfRange(i + 1, i + 1 + pid.length)
            out[pid] = pid.decode(data.toUnsigned())
            i += 1 + pid.length
        }
        return out
    }

    /** Returns the data bytes (after service and pid) of a single-PID request, or null on NO DATA. */
    private suspend fun request(service: Int, pid: Int): ByteArray? {
        val cmd = "%02X%02X".format(service, pid)
        val payload = singleResponse(cmd, expectedService = service + 0x40) ?: return null
        if (payload.size < 2 || payload[1] != pid.toByte()) return null
        return payload.copyOfRange(2, payload.size)
    }

    private suspend fun singleResponse(cmd: String, expectedService: Int): ByteArray? {
        val wire = if (responseCount) cmd + "1" else cmd // trailing digit: stop waiting after N responses
        val lines = try {
            elm.command(wire)
        } catch (e: ElmException.NoData) {
            return null
        } catch (e: ElmException.Unsupported) {
            if (!responseCount) throw e
            responseCount = false
            return singleResponse(cmd, expectedService)
        }
        return Frames.payloads(lines).firstOrNull { it.firstOrNull() == expectedService.toByte() }
    }

    internal fun decodeSupportMask(base: Int, data: ByteArray): Set<Int> {
        val out = mutableSetOf<Int>()
        var bits = 0L
        for (i in 0 until minOf(4, data.size)) bits = (bits shl 8) or (data[i].toLong() and 0xFF)
        for (n in 0 until 32) if ((bits shr (31 - n)) and 1L == 1L) out += base + n + 1
        return out
    }

    private fun ByteArray.toUnsigned() = IntArray(size) { this[it].toInt() and 0xFF }

    private companion object {
        const val MAX_BATCH = 6 // J1979 limit for PIDs per request
    }
}

package dev.obdscanner.core.sim

import dev.obdscanner.core.transport.Transport
import kotlin.math.sin

/**
 * In-memory ELM327 behaving like a CAN (ISO 15765-4, protocol 6) diesel car.
 * Used for unit tests and the app's demo mode.
 */
class SimulatedElm327(
    var storedDtcs: MutableList<Int> = mutableListOf(0x0299, 0x0401),
    private val vin: String = "TMBAB61Z092000000",
    private val supportsMultiPid: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
) : Transport {
    private var open = false
    private var echo = true
    private var spaces = true
    private var pending: String? = null
    private val start = clock()

    val log = mutableListOf<String>()

    override suspend fun open() { open = true }
    override suspend fun close() { open = false }

    override suspend fun write(data: String) {
        check(open) { "closed" }
        val cmd = data.trim().uppercase().replace(" ", "")
        log += cmd
        pending = (if (echo) cmd + "\r" else "") + respond(cmd) + "\r\r>"
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String {
        val p = checkNotNull(pending) { "no pending response" }
        pending = null
        return p.removeSuffix(">")
    }

    private fun respond(cmd: String): String = when {
        cmd == "ATZ" -> { echo = true; spaces = true; "\rELM327 v1.5" }
        cmd == "ATE0" -> { echo = false; "OK" }
        cmd == "ATS0" -> { spaces = false; "OK" }
        cmd.startsWith("AT") && cmd != "ATDPN" -> "OK"
        cmd == "ATDPN" -> "A6"
        cmd.startsWith("01") -> mode01(cmd.removePrefix("01"))
        cmd == "03" -> dtcResponse(0x43)
        cmd == "07" -> "NO DATA"
        cmd == "0A" -> "NO DATA"
        cmd == "04" -> { storedDtcs.clear(); fmt(0x44) }
        cmd.startsWith("0902") -> vinResponse()
        else -> "?"
    }

    private fun mode01(rest: String): String {
        // Optional trailing single-digit response count (e.g. "010C1").
        val hex = if (rest.length % 2 == 1) rest.dropLast(1) else rest
        val pids = hex.chunked(2).map { it.toInt(16) }
        if (pids.isEmpty()) return "?"
        if (pids.size > 1 && !supportsMultiPid) return "NO DATA"
        val body = ArrayList<Int>()
        body += 0x41
        for (pid in pids) body += pidBytes(pid) ?: return "NO DATA"
        return fmt(*body.toIntArray())
    }

    private fun pidBytes(pid: Int): List<Int>? {
        val t = (clock() - start) / 1000.0
        val rpm = (1500 + 800 * sin(t / 3)).toInt()
        val speed = (60 + 30 * sin(t / 5)).toInt()
        return when (pid) {
            0x00 -> listOf(pid, 0x98, 0x3A, 0x80, 0x13)
            0x20 -> listOf(pid, 0x80, 0x01, 0xA0, 0x01)
            0x40 -> listOf(pid, 0x44, 0x00, 0x00, 0x00)
            0x04 -> listOf(pid, 90)
            0x05 -> listOf(pid, 90 + 40)
            0x0B -> listOf(pid, 130)
            0x0C -> listOf(pid, (rpm * 4) shr 8, (rpm * 4) and 0xFF)
            0x0D -> listOf(pid, speed)
            0x0F -> listOf(pid, 35 + 40)
            0x10 -> listOf(pid, 0x03, 0xE8) // 10.00 g/s
            0x23 -> listOf(pid, 0x0B, 0xB8) // 30000 kPa
            0x2F -> listOf(pid, 128)
            0x42 -> listOf(pid, 0x37, 0x4B) // 14.155 V
            else -> null
        }
    }

    private fun dtcResponse(service: Int): String {
        val bytes = mutableListOf(service, storedDtcs.size)
        storedDtcs.forEach { bytes += it shr 8; bytes += it and 0xFF }
        return fmt(*bytes.toIntArray())
    }

    private fun vinResponse(): String {
        val data = listOf(0x49, 0x02, 0x01) + vin.map { it.code }
        val total = data.size
        val frames = data.chunked(7)
        val sb = StringBuilder("%03X\r".format(total))
        frames.forEachIndexed { i, f ->
            sb.append("%X:".format(i and 0xF)).append(fmt(*f.toIntArray())).append('\r')
        }
        return sb.toString().trimEnd('\r')
    }

    private fun fmt(vararg bytes: Int): String =
        bytes.joinToString(if (spaces) " " else "") { "%02X".format(it) }
}

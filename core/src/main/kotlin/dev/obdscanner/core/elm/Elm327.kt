package dev.obdscanner.core.elm

import dev.obdscanner.core.transport.Transport
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Failure reported by the adapter itself or by the vehicle bus, as opposed to a transport I/O failure. */
sealed class ElmException(message: String) : Exception(message) {
    class NoData : ElmException("NO DATA")
    class UnableToConnect : ElmException("UNABLE TO CONNECT")
    class BusError(detail: String) : ElmException(detail)
    class Unsupported(command: String) : ElmException("Adapter rejected '$command'")
}

/**
 * Thin, serialised command layer over an ELM327-compatible adapter.
 * Knows about AT commands, prompts and error strings, but nothing about OBD semantics.
 */
class Elm327(
    private val transport: Transport,
    private val defaultTimeoutMs: Long = 2_500,
) {
    private val lock = Mutex()

    suspend fun open() = transport.open()
    suspend fun close() = transport.close()

    /** Sends [command] and returns the cleaned response lines (echo, blanks and "SEARCHING..." removed). */
    suspend fun command(command: String, timeoutMs: Long = defaultTimeoutMs): List<String> = lock.withLock {
        transport.write(command + "\r")
        val raw = transport.readUntilPrompt(timeoutMs)
        parseLines(command, raw)
    }

    /** AT-style setup command that must answer OK. */
    suspend fun setup(command: String) {
        val reply = command(command)
        if (reply.none { it.equals("OK", ignoreCase = true) }) throw ElmException.Unsupported(command)
    }

    /** Resets the adapter and returns its version string (e.g. "ELM327 v1.5"). */
    suspend fun reset(): String {
        val reply = command("ATZ", timeoutMs = 6_000)
        delay(0)
        return reply.firstOrNull { it.contains("ELM", ignoreCase = true) || it.contains("STN", ignoreCase = true) }
            ?: reply.firstOrNull().orEmpty()
    }

    companion object {
        internal fun parseLines(command: String, raw: String): List<String> {
            val lines = raw.split('\r', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { it.equals(command, ignoreCase = true) } // echo
                .filterNot { it.startsWith("SEARCHING", ignoreCase = true) }

            for (line in lines) {
                val up = line.uppercase()
                when {
                    up == "NO DATA" -> throw ElmException.NoData()
                    up == "UNABLE TO CONNECT" -> throw ElmException.UnableToConnect()
                    up.startsWith("BUS INIT") && up.contains("ERROR") -> throw ElmException.BusError(line)
                    up == "CAN ERROR" || up == "BUS ERROR" || up == "BUS BUSY" ||
                        up == "FB ERROR" || up == "DATA ERROR" || up == "BUFFER FULL" ||
                        up == "STOPPED" || up == "ERROR" -> throw ElmException.BusError(line)
                    up == "?" -> throw ElmException.Unsupported(command)
                }
            }
            return lines
        }
    }
}

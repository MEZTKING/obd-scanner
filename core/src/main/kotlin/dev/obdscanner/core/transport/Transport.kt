package dev.obdscanner.core.transport

/**
 * Byte pipe to an ELM327-compatible adapter (Wi-Fi socket, Bluetooth SPP, simulator).
 * Implementations only move text; all protocol knowledge lives in [dev.obdscanner.core.elm.Elm327].
 */
interface Transport {
    suspend fun open()
    suspend fun close()

    /** Sends [data] verbatim; the caller includes the trailing carriage return. */
    suspend fun write(data: String)

    /**
     * Reads until the adapter's `>` prompt and returns everything before it.
     * Throws [kotlinx.coroutines.TimeoutCancellationException] when no prompt arrives in [timeoutMs].
     */
    suspend fun readUntilPrompt(timeoutMs: Long): String
}

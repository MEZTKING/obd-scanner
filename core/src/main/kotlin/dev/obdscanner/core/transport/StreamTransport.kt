package dev.obdscanner.core.transport

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Shared logic for transports backed by a blocking input/output stream pair. */
abstract class StreamTransport : Transport {
    protected abstract var input: InputStream?
    protected abstract var output: OutputStream?

    override suspend fun write(data: String) = withContext(Dispatchers.IO) {
        val out = output ?: error("Transport not open")
        out.write(data.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    override suspend fun readUntilPrompt(timeoutMs: Long): String {
        val stream = input ?: error("Transport not open")
        return withTimeout(timeoutMs) {
            withContext(Dispatchers.IO) {
                val buffer = StringBuilder()
                while (true) {
                    // Poll with available() so the timeout's cancellation is observed between reads.
                    ensureActive()
                    if (stream.available() == 0) {
                        Thread.sleep(2)
                        continue
                    }
                    val b = stream.read()
                    if (b < 0) error("Adapter closed the connection")
                    if (b.toChar() == '>') break
                    buffer.append(b.toChar())
                }
                buffer.toString()
            }
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { input?.close() }
        runCatching { output?.close() }
        input = null
        output = null
    }
}

/**
 * Wi-Fi adapters. vLinker MC+ default is 192.168.0.10:35000 (check the adapter's label/manual).
 */
class TcpTransport(
    private val host: String = "192.168.0.10",
    private val port: Int = 35000,
    private val connectTimeoutMs: Int = 4000,
) : StreamTransport() {
    private var socket: Socket? = null
    override var input: InputStream? = null
    override var output: OutputStream? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket = s
        input = s.getInputStream()
        output = s.getOutputStream()
    }

    override suspend fun close() {
        super.close()
        withContext(Dispatchers.IO) { runCatching { socket?.close() } }
        socket = null
    }
}

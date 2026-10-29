package dev.obdscanner.app.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import dev.obdscanner.core.transport.StreamTransport
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bluetooth Classic serial port profile, as used by the vLinker MC+ and most ELM327 clones. */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is requested by the UI before any device is chosen
class BluetoothSppTransport(private val device: BluetoothDevice) : StreamTransport() {
    private var socket: BluetoothSocket? = null
    override var input: InputStream? = null
    override var output: OutputStream? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        val s = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: Exception) {
            // Some adapters only answer on channel 1 without an SDP lookup.
            fallbackSocket(device)?.also { it.connect() } ?: throw e
        }
        socket = s
        input = s.inputStream
        output = s.outputStream
    }

    private fun fallbackSocket(device: BluetoothDevice): BluetoothSocket? = runCatching {
        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        m.invoke(device, 1) as BluetoothSocket
    }.getOrNull()

    override suspend fun close() {
        super.close()
        withContext(Dispatchers.IO) { runCatching { socket?.close() } }
        socket = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

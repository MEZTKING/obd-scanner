package dev.obdscanner.app.data

import dev.obdscanner.core.sim.SimulatedElm327
import dev.obdscanner.core.transport.Transport
import kotlinx.coroutines.delay

/** Simulator wrapped with a realistic per-command latency, for trying the app without a car. */
class DemoTransport : Transport {
    private val sim = SimulatedElm327()
    override suspend fun open() = sim.open()
    override suspend fun close() = sim.close()
    override suspend fun write(data: String) = sim.write(data)
    override suspend fun readUntilPrompt(timeoutMs: Long): String {
        delay(40)
        return sim.readUntilPrompt(timeoutMs)
    }
}

package dev.obdscanner.core

import dev.obdscanner.core.elm.Elm327
import dev.obdscanner.core.elm.Frames
import dev.obdscanner.core.obd.DtcDecoder
import dev.obdscanner.core.obd.DtcKind
import dev.obdscanner.core.obd.ObdClient
import dev.obdscanner.core.obd.Pids
import dev.obdscanner.core.sim.SimulatedElm327
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObdClientTest {
    private fun client(sim: SimulatedElm327 = SimulatedElm327()) = ObdClient(Elm327(sim)) to sim

    @Test fun decodesRpm() {
        assertEquals(1726.0, Pids.RPM.decode(intArrayOf(0x1A, 0xF8)))
    }

    @Test fun decodesCoolantAndLoad() {
        assertEquals(90.0, Pids.COOLANT_TEMP.decode(intArrayOf(130)))
        assertEquals(100.0, Pids.ENGINE_LOAD.decode(intArrayOf(255)))
    }

    @Test fun decodesDtcs() {
        assertEquals("P0133", DtcDecoder.decode(0x01, 0x33))
        assertEquals("C0123", DtcDecoder.decode(0x41, 0x23))
        assertEquals("B0001", DtcDecoder.decode(0x80, 0x01))
        assertEquals("U0001", DtcDecoder.decode(0xC0, 0x01))
    }

    @Test fun assemblesMultiFrameResponses() {
        val payloads = Frames.payloads(listOf("00A", "0: 49 02 01 54 4D 42", "1: 41 42 36 31 5A 30"))
        assertEquals(1, payloads.size)
        assertEquals(10, payloads[0].size)
    }

    @Test fun connectsAndDetectsCan() = runTest {
        val (c, _) = client()
        val info = c.connect()
        assertTrue(info.isCan)
        assertEquals("6", info.protocol)
    }

    @Test fun listsSupportedPids() = runTest {
        val (c, _) = client()
        c.connect()
        val pids = c.supportedPids()
        assertTrue(0x0C in pids && 0x0D in pids && 0x05 in pids)
    }

    @Test fun readsBatchedPidsInOneRequest() = runTest {
        val (c, sim) = client()
        c.connect()
        sim.log.clear()
        val values = c.read(listOf(Pids.RPM, Pids.SPEED, Pids.COOLANT_TEMP))
        assertEquals(3, values.size)
        assertEquals(90.0, values[Pids.COOLANT_TEMP])
        assertEquals(1, sim.log.size)
    }

    @Test fun fallsBackWhenBatchingUnsupported() = runTest {
        val (c, sim) = client(SimulatedElm327(supportsMultiPid = false))
        c.connect()
        sim.log.clear()
        val values = c.read(listOf(Pids.RPM, Pids.SPEED))
        assertEquals(2, values.size)
        assertEquals(3, sim.log.size) // failed batch + two single requests
    }

    @Test fun readsAndClearsDtcs() = runTest {
        val (c, _) = client()
        c.connect()
        assertEquals(listOf("P0299", "P0401"), c.readDtcs(DtcKind.STORED).map { it.code })
        assertEquals("Turbocharger underboost condition", c.readDtcs(DtcKind.STORED).first().description)
        assertTrue(c.clearDtcs())
        assertTrue(c.readDtcs(DtcKind.STORED).isEmpty())
        assertTrue(c.readDtcs(DtcKind.PENDING).isEmpty())
    }

    @Test fun readsVin() = runTest {
        val (c, _) = client()
        c.connect()
        assertEquals("TMBAB61Z092000000", c.readVin())
    }

    @Test fun unsupportedPidReturnsNothing() = runTest {
        val (c, _) = client()
        c.connect()
        assertNull(c.read(listOf(Pids.OIL_TEMP)).values.firstOrNull())
    }
}

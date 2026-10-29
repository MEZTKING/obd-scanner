package dev.obdscanner.core

import dev.obdscanner.core.obd.Pids
import dev.obdscanner.core.obd.TripComputer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TripComputerTest {
    @Test fun integratesDistanceAndEcuFuelRate() {
        val t = TripComputer()
        val sample = mapOf(Pids.SPEED to 90.0, Pids.FUEL_RATE to 6.0)
        t.update(sample, 0)
        t.update(sample, 3_600_000) // 1 h, but each step is capped at 5 s
        t.update(sample, 3_605_000)
        val s = t.summary()
        assertEquals(10.0, s.durationSec, 1e-9)
        assertEquals(0.25, s.distanceKm, 1e-9)
        assertTrue(!s.fuelIsEstimated)
        assertEquals(6.0 / 90 * 100, s.litresPer100Km!!, 1e-9)
    }

    @Test fun estimatesFromLoadWhenNoFuelRatePid() {
        val t = TripComputer()
        t.update(mapOf(Pids.ENGINE_LOAD to 100.0, Pids.RPM to 4000.0), 0)
        assertEquals(20.0, t.fuelRateLph!!, 1e-9)
        t.update(mapOf(Pids.ENGINE_LOAD to 20.0, Pids.RPM to 900.0), 1000)
        assertEquals(0.9, t.fuelRateLph!!, 1e-9)
        assertTrue(t.summary().fuelIsEstimated)
    }
}

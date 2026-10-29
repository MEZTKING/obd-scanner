package dev.obdscanner.core.obd

data class TripSummary(
    val durationSec: Double,
    val distanceKm: Double,
    val fuelLitres: Double,
    val avgSpeedKmh: Double,
    val litresPer100Km: Double?,
    val fuelIsEstimated: Boolean,
)

/**
 * Integrates speed and fuel rate over time.
 *
 * Fuel: uses the ECU's fuel-rate PID when present. Otherwise estimates from engine load and RPM
 * (fuel ≈ k · load% · rpm). MAF-based estimates are wrong on a diesel (no fixed air/fuel ratio), so
 * they are deliberately not used. [calibration] scales the estimate; set it from real fill-ups.
 */
class TripComputer(
    private val calibration: Double = 1.0,
    private val fullLoadLitresPerHour: Double = 20.0, // ~77 kW diesel at rated rpm
    private val ratedRpm: Double = 4000.0,
) {
    private var lastTimeMs: Long? = null
    var durationSec = 0.0; private set
    var distanceKm = 0.0; private set
    var fuelLitres = 0.0; private set
    var fuelIsEstimated = false; private set

    /** Instantaneous fuel rate in L/h for the last sample, or null before any sample. */
    var fuelRateLph: Double? = null; private set

    fun reset() {
        lastTimeMs = null; durationSec = 0.0; distanceKm = 0.0; fuelLitres = 0.0; fuelRateLph = null
    }

    fun update(values: Map<Pid, Double>, nowMs: Long) {
        val rate = fuelRate(values)
        fuelRateLph = rate
        val speed = values[Pids.SPEED]
        val prev = lastTimeMs
        lastTimeMs = nowMs
        if (prev == null) return
        val dtH = (nowMs - prev).coerceIn(0, 5_000) / 3_600_000.0
        durationSec += dtH * 3600
        if (speed != null) distanceKm += speed * dtH
        if (rate != null) fuelLitres += rate * dtH
    }

    fun summary(): TripSummary = TripSummary(
        durationSec = durationSec,
        distanceKm = distanceKm,
        fuelLitres = fuelLitres,
        avgSpeedKmh = if (durationSec > 0) distanceKm / (durationSec / 3600) else 0.0,
        litresPer100Km = if (distanceKm > 0.1) fuelLitres / distanceKm * 100 else null,
        fuelIsEstimated = fuelIsEstimated,
    )

    private fun fuelRate(values: Map<Pid, Double>): Double? {
        values[Pids.FUEL_RATE]?.let { fuelIsEstimated = false; return it }
        val load = values[Pids.ENGINE_LOAD] ?: return null
        val rpm = values[Pids.RPM] ?: return null
        fuelIsEstimated = true
        return calibration * fullLoadLitresPerHour * (load / 100.0) * (rpm / ratedRpm)
    }
}

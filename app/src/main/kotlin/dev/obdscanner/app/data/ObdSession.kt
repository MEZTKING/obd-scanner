package dev.obdscanner.app.data

import dev.obdscanner.core.elm.Elm327
import dev.obdscanner.core.obd.AdapterInfo
import dev.obdscanner.core.obd.Dtc
import dev.obdscanner.core.obd.DtcKind
import dev.obdscanner.core.obd.ObdClient
import dev.obdscanner.core.obd.Pid
import dev.obdscanner.core.obd.Pids
import dev.obdscanner.core.obd.TripComputer
import dev.obdscanner.core.obd.TripSummary
import dev.obdscanner.core.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val step: String) : ConnectionState
    data class Connected(val info: AdapterInfo, val vin: String?) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class DtcReport(val stored: List<Dtc>, val pending: List<Dtc>, val permanent: List<Dtc>)

/**
 * Owns the adapter connection and the live polling loop. One instance for the whole app;
 * the process keeps it alive while the activity is recreated.
 */
class ObdSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: ObdClient? = null
    private var pollJob: Job? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private val _live = MutableStateFlow<Map<Pid, Double>>(emptyMap())
    val live: StateFlow<Map<Pid, Double>> = _live

    private val _supported = MutableStateFlow<Set<Int>>(emptySet())
    val supported: StateFlow<Set<Int>> = _supported

    private val _trip = MutableStateFlow(TripSummary(0.0, 0.0, 0.0, 0.0, null, true))
    val trip: StateFlow<TripSummary> = _trip
    private val tripComputer = TripComputer()

    private val _dtcs = MutableStateFlow<DtcReport?>(null)
    val dtcs: StateFlow<DtcReport?> = _dtcs

    private val _rate = MutableStateFlow(0.0)
    /** Dashboard refresh rate, samples per second. */
    val rate: StateFlow<Double> = _rate

    /** Which PIDs the dashboard polls. Only ones the ECU reports as supported are actually requested. */
    val dashboardPids = listOf(
        Pids.RPM, Pids.SPEED, Pids.COOLANT_TEMP, Pids.ENGINE_LOAD, Pids.MAP, Pids.INTAKE_TEMP,
        Pids.MAF, Pids.THROTTLE, Pids.FUEL_RAIL_PRESSURE, Pids.MODULE_VOLTAGE, Pids.FUEL_RATE,
    )

    fun connect(transport: Transport) {
        scope.launch {
            disconnectInternal()
            val c = ObdClient(Elm327(transport))
            try {
                _state.value = ConnectionState.Connecting("Opening link")
                val info = c.connect()
                _state.value = ConnectionState.Connecting("Protocol ${info.protocol}, reading VIN")
                val vin = runCatching { c.readVin() }.getOrNull()
                _state.value = ConnectionState.Connecting("Listing supported PIDs")
                _supported.value = runCatching { c.supportedPids() }.getOrDefault(emptySet())
                client = c
                _state.value = ConnectionState.Connected(info, vin)
                startPolling(c)
            } catch (e: Exception) {
                runCatching { c.disconnect() }
                _state.value = ConnectionState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun disconnect() {
        scope.launch { disconnectInternal() }
    }

    fun resetTrip() {
        tripComputer.reset()
        _trip.value = tripComputer.summary()
    }

    fun readDtcs() {
        val c = client ?: return
        scope.launch {
            pausePolling {
                _dtcs.value = DtcReport(
                    stored = runCatching { c.readDtcs(DtcKind.STORED) }.getOrDefault(emptyList()),
                    pending = runCatching { c.readDtcs(DtcKind.PENDING) }.getOrDefault(emptyList()),
                    permanent = runCatching { c.readDtcs(DtcKind.PERMANENT) }.getOrDefault(emptyList()),
                )
            }
        }
    }

    fun clearDtcs() {
        val c = client ?: return
        scope.launch {
            pausePolling { runCatching { c.clearDtcs() } }
            readDtcs()
        }
    }

    private suspend fun disconnectInternal() {
        pollJob?.cancelAndJoin()
        pollJob = null
        client?.let { runCatching { it.disconnect() } }
        client = null
        _live.value = emptyMap()
        _dtcs.value = null
        _state.value = ConnectionState.Disconnected
    }

    private fun startPolling(c: ObdClient) {
        pollJob = scope.launch {
            val pids = dashboardPids.filter { it.id in _supported.value }.ifEmpty { dashboardPids }
            var samples = 0
            var windowStart = System.currentTimeMillis()
            while (isActive) {
                try {
                    val values = c.read(pids)
                    val now = System.currentTimeMillis()
                    _live.value = values
                    tripComputer.update(values, now)
                    _trip.value = tripComputer.summary()
                    samples++
                    if (now - windowStart >= 2000) {
                        _rate.value = samples * 1000.0 / (now - windowStart)
                        samples = 0; windowStart = now
                    }
                } catch (e: Exception) {
                    _state.update { s -> if (s is ConnectionState.Connected) ConnectionState.Failed("Lost link: ${e.message}") else s }
                    runCatching { c.disconnect() }
                    client = null
                    return@launch
                }
            }
        }
    }

    private suspend fun pausePolling(block: suspend () -> Unit) {
        val c = client ?: return
        pollJob?.cancelAndJoin()
        try { block() } finally { if (client != null) startPolling(c) }
    }

    /** Demo-mode delay so the simulator looks like a real adapter. */
    suspend fun throttleDemo() = delay(50)
}

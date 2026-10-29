package dev.obdscanner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.obdscanner.app.data.ObdSession
import java.util.Locale

@Composable
fun TripScreen(session: ObdSession, modifier: Modifier = Modifier) {
    val trip by session.trip.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Distance", String.format(Locale.US, "%.1f", trip.distanceKm), "km", Modifier.weight(1f))
            Stat("Time", formatDuration(trip.durationSec), "", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Avg speed", String.format(Locale.US, "%.0f", trip.avgSpeedKmh), "km/h", Modifier.weight(1f))
            Stat("Fuel", String.format(Locale.US, "%.2f", trip.fuelLitres), "L", Modifier.weight(1f))
        }
        Stat(
            "Consumption",
            trip.litresPer100Km?.let { String.format(Locale.US, "%.1f", it) } ?: "–",
            "L/100 km",
            Modifier.fillMaxWidth(),
        )
        if (trip.fuelIsEstimated) {
            Text(
                "Fuel is estimated from engine load and RPM because this ECU does not report fuel rate. " +
                    "Treat it as relative, not exact, until calibrated against a real fill-up.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = session::resetTrip) { Text("Reset trip") }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(unit, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatDuration(sec: Double): String {
    val s = sec.toLong()
    return String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
}

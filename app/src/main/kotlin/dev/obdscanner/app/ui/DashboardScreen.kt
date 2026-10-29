package dev.obdscanner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.obdscanner.app.data.ConnectionState
import dev.obdscanner.app.data.ObdSession
import dev.obdscanner.core.obd.Pid
import java.util.Locale

@Composable
fun DashboardScreen(session: ObdSession, modifier: Modifier = Modifier) {
    val state by session.state.collectAsStateWithLifecycle()
    val live by session.live.collectAsStateWithLifecycle()
    val rate by session.rate.collectAsStateWithLifecycle()

    if (state !is ConnectionState.Connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Connect to an adapter first") }
        return
    }
    Column(modifier.fillMaxSize()) {
        Text(
            String.format(Locale.US, "%.1f samples/s", rate),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(session.dashboardPids.filter { it in live }, key = { it.id }) { pid ->
                Gauge(pid, live[pid]!!)
            }
        }
    }
}

@Composable
private fun Gauge(pid: Pid, value: Double) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(pid.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(
                formatValue(pid, value),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(pid.unit, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun formatValue(pid: Pid, v: Double): String = when (pid.unit) {
    "rpm", "km/h", "kPa", "°C" -> String.format(Locale.US, "%.0f", v)
    "V" -> String.format(Locale.US, "%.2f", v)
    else -> String.format(Locale.US, "%.1f", v)
}

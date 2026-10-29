package dev.obdscanner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.obdscanner.app.data.ConnectionState
import dev.obdscanner.app.data.ObdSession
import dev.obdscanner.core.obd.Dtc

@Composable
fun FaultsScreen(session: ObdSession, modifier: Modifier = Modifier) {
    val state by session.state.collectAsStateWithLifecycle()
    val report by session.dtcs.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    if (state !is ConnectionState.Connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Connect to an adapter first") }
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = session::readDtcs, modifier = Modifier.weight(1f)) { Text("Read codes") }
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.weight(1f)) { Text("Clear codes") }
        }
        val r = report
        if (r == null) {
            Text("Tap Read codes.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Section("Stored (MIL)", r.stored)
            Section("Pending", r.pending)
            Section("Permanent", r.permanent)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear fault codes?") },
            text = { Text("This also erases freeze-frame data and resets readiness monitors. Do it with the engine off and ignition on.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; session.clearDtcs() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, codes: List<Dtc>) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (codes.isEmpty()) {
        Text("None", style = MaterialTheme.typography.bodyMedium)
        return
    }
    codes.forEach { dtc ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(dtc.code, style = MaterialTheme.typography.titleLarge)
                Text(dtc.description ?: "No description in the built-in database", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

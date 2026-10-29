package dev.obdscanner.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.obdscanner.app.data.ConnectionState
import dev.obdscanner.app.data.DemoTransport
import dev.obdscanner.app.data.ObdSession
import dev.obdscanner.app.transport.BluetoothSppTransport
import dev.obdscanner.core.transport.TcpTransport

@Composable
fun ConnectScreen(session: ObdSession, modifier: Modifier = Modifier, onConnected: () -> Unit) {
    val state by session.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var host by rememberSaveable { mutableStateOf("192.168.0.10") }
    var port by rememberSaveable { mutableStateOf("35000") }
    var btGranted by remember { mutableStateOf(hasBluetoothPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        btGranted = it
    }

    LaunchedEffect(state) { if (state is ConnectionState.Connected) onConnected() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(state, onDisconnect = session::disconnect)

        Text("Bluetooth (paired devices)", style = MaterialTheme.typography.titleMedium)
        if (!btGranted) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else btGranted = true
            }) { Text("Allow Bluetooth") }
        } else {
            val devices = remember(btGranted) { pairedDevices(context) }
            if (devices.isEmpty()) {
                Text("No paired devices. Pair the vLinker in Android's Bluetooth settings first.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            devices.forEach { d ->
                DeviceRow(d) { session.connect(BluetoothSppTransport(d)) }
            }
        }

        HorizontalDivider()
        Text("Wi-Fi", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it }, label = { Text("Host") }, modifier = Modifier.weight(2f), singleLine = true)
            OutlinedTextField(port, { port = it }, label = { Text("Port") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Button(
            onClick = { session.connect(TcpTransport(host.trim(), port.trim().toIntOrNull() ?: 35000)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect over Wi-Fi") }
        Text("Join the adapter's Wi-Fi network first. The phone will have no internet while connected.",
            style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        OutlinedButton(onClick = { session.connect(DemoTransport()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Demo mode (simulated car)")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusCard(state: ConnectionState, onDisconnect: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (state) {
                ConnectionState.Disconnected -> Text("Not connected", style = MaterialTheme.typography.titleMedium)
                is ConnectionState.Connecting -> Row {
                    CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(state.step)
                }
                is ConnectionState.Connected -> {
                    Text("Connected", style = MaterialTheme.typography.titleMedium)
                    Text("Adapter: ${state.info.version}")
                    Text("Protocol: ${protocolName(state.info.protocol)}")
                    Text("VIN: ${state.vin ?: "not reported"}")
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                }
                is ConnectionState.Failed -> {
                    Text("Connection failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(state.message)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(device: BluetoothDevice, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(device.name ?: "Unnamed device", style = MaterialTheme.typography.bodyLarge)
            Text(device.address, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun pairedDevices(context: Context): List<BluetoothDevice> {
    if (!hasBluetoothPermission(context)) return emptyList()
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter ?: return emptyList()
    return runCatching { adapter.bondedDevices.toList() }.getOrDefault(emptyList())
        .sortedByDescending { d -> d.name?.contains("vLink", ignoreCase = true) == true || d.name?.contains("OBD", ignoreCase = true) == true }
}

private fun protocolName(code: String) = when (code) {
    "1" -> "SAE J1850 PWM"; "2" -> "SAE J1850 VPW"; "3" -> "ISO 9141-2"
    "4" -> "ISO 14230-4 KWP (5 baud)"; "5" -> "ISO 14230-4 KWP (fast)"
    "6" -> "ISO 15765-4 CAN 11-bit 500k"; "7" -> "ISO 15765-4 CAN 29-bit 500k"
    "8" -> "ISO 15765-4 CAN 11-bit 250k"; "9" -> "ISO 15765-4 CAN 29-bit 250k"
    else -> "code $code"
}

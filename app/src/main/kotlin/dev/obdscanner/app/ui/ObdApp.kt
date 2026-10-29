package dev.obdscanner.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.obdscanner.app.data.ObdSession

enum class Tab(val label: String, val icon: ImageVector) {
    CONNECT("Connect", Icons.Default.Bluetooth),
    DASHBOARD("Live", Icons.Default.Speed),
    FAULTS("Faults", Icons.Default.Warning),
    TRIP("Trip", Icons.Default.Route),
}

@Composable
fun ObdApp(session: ObdSession) {
    var tab by rememberSaveable { mutableStateOf(Tab.CONNECT) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            Tab.CONNECT -> ConnectScreen(session, m, onConnected = { tab = Tab.DASHBOARD })
            Tab.DASHBOARD -> DashboardScreen(session, m)
            Tab.FAULTS -> FaultsScreen(session, m)
            Tab.TRIP -> TripScreen(session, m)
        }
    }
}

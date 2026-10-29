package dev.obdscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import dev.obdscanner.app.ui.ObdApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = (application as App).session
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ObdApp(session)
            }
        }
    }
}

package dev.obdscanner.app

import android.app.Application
import dev.obdscanner.app.data.ObdSession

class App : Application() {
    val session = ObdSession()
}

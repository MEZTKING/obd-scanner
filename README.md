# obd-scanner

Android OBD-II scanner for ELM327-compatible adapters (developed against a vLinker MC+),
targeting a 2009 Škoda Octavia II (1Z) 1.9 TDI, engine code BXE.

## Status

| Area | State |
| --- | --- |
| `:core` transport (Wi-Fi TCP), ELM327 command layer, J1979 client | done, unit-tested against a simulated adapter |
| Standard PIDs, multi-PID batching, DTC read/clear, VIN | done, unit-tested |
| Bluetooth Classic (SPP) transport | written, compiles, not yet tried on a device |
| Android app (Compose: connect, live dashboard, faults, trip) | written, compiles, not yet run on a phone |
| Trip computer (distance, estimated diesel fuel use) | done, unit-tested; estimate needs calibration against fill-ups |
| VAG-specific diagnostics (KWP2000 over TP 2.0, measuring blocks) | planned |

Nothing here has been run against a real car or a real vLinker yet. The simulator only proves the
code is self-consistent, not that it matches the adapter's quirks. The app has been compiled but
not launched on a phone or emulator; expect first-run bugs.

## Layout

- `core/` – pure Kotlin/JVM, no Android dependencies, so it is fast to test.
  - `transport/` – `Transport` interface, `TcpTransport` (default `192.168.0.10:35000`)
  - `elm/` – `Elm327` (AT commands, error strings), `Frames` (line/ISO-TP payload parsing)
  - `obd/` – `ObdClient`, `Pids`, `DtcDecoder`/`DtcDatabase`
    - `obd/TripComputer` – distance/fuel integration
  - `sim/` – `SimulatedElm327`, used by tests and the app's demo mode
- `app/` – Android (Kotlin, Jetpack Compose, minSdk 26).
  - `transport/BluetoothSppTransport` – Bluetooth Classic SPP
  - `data/ObdSession` – owns the connection and the polling loop; `DemoTransport` wraps the simulator
  - `ui/` – Connect, Live, Faults, Trip screens

## Build and test

```
./gradlew :core:test            # protocol engine tests, no Android SDK needed
./gradlew :app:assembleDebug    # needs Android SDK (platform 35); set sdk.dir in local.properties
```

Requires JDK 17+. Install `app/build/outputs/apk/debug/app-debug.apk` on the phone, or open the
project in Android Studio.

## Using it with the vLinker MC+

- **Bluetooth:** pair the adapter in Android's Bluetooth settings first (default PIN is usually
  1234), then pick it from the list on the Connect tab.
- **Wi-Fi:** join the adapter's network, then connect to `192.168.0.10:35000` (check the adapter's
  manual if that differs). The phone has no internet while on that network.
- **Demo mode** runs against the simulator so the screens can be tried without a car.

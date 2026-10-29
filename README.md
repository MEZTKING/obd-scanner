# obd-scanner

Android OBD-II scanner for ELM327-compatible adapters (developed against a vLinker MC+),
targeting a 2009 Škoda Octavia II (1Z) 1.9 TDI, engine code BXE.

## Status

| Area | State |
| --- | --- |
| `:core` transport (Wi-Fi TCP), ELM327 command layer, J1979 client | done, unit-tested against a simulated adapter |
| Standard PIDs, multi-PID batching, DTC read/clear, VIN | done, unit-tested |
| Bluetooth Classic (SPP) transport | planned (Android module) |
| Android app (Compose dashboard, DTC screen, trips) | planned |
| VAG-specific diagnostics (KWP2000 over TP 2.0, measuring blocks) | planned |

Nothing here has been run against a real car or a real vLinker yet. The simulator only proves the
code is self-consistent, not that it matches the adapter's quirks.

## Layout

- `core/` – pure Kotlin/JVM, no Android dependencies, so it is fast to test.
  - `transport/` – `Transport` interface, `TcpTransport` (default `192.168.0.10:35000`)
  - `elm/` – `Elm327` (AT commands, error strings), `Frames` (line/ISO-TP payload parsing)
  - `obd/` – `ObdClient`, `Pids`, `DtcDecoder`/`DtcDatabase`
  - `sim/` – `SimulatedElm327`, used by tests and later by the app's demo mode

## Build and test

```
./gradlew :core:test
```

Requires JDK 17+.

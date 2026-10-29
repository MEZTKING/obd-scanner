package dev.obdscanner.core.obd

/** A Mode 01 parameter. [decode] receives the raw data bytes (as unsigned ints) A, B, C, D. */
class Pid(
    val id: Int,
    val name: String,
    val unit: String,
    val length: Int,
    val decode: (IntArray) -> Double,
) {
    val hex: String get() = "%02X".format(id)
    override fun toString() = "Pid($hex $name)"
}

object Pids {
    val ENGINE_LOAD = Pid(0x04, "Engine load", "%", 1) { it[0] * 100.0 / 255 }
    val COOLANT_TEMP = Pid(0x05, "Coolant temperature", "°C", 1) { it[0] - 40.0 }
    val FUEL_PRESSURE = Pid(0x0A, "Fuel pressure", "kPa", 1) { it[0] * 3.0 }
    val MAP = Pid(0x0B, "Intake manifold pressure", "kPa", 1) { it[0].toDouble() }
    val RPM = Pid(0x0C, "Engine speed", "rpm", 2) { (it[0] * 256 + it[1]) / 4.0 }
    val SPEED = Pid(0x0D, "Vehicle speed", "km/h", 1) { it[0].toDouble() }
    val INTAKE_TEMP = Pid(0x0F, "Intake air temperature", "°C", 1) { it[0] - 40.0 }
    val MAF = Pid(0x10, "Mass air flow", "g/s", 2) { (it[0] * 256 + it[1]) / 100.0 }
    val THROTTLE = Pid(0x11, "Throttle position", "%", 1) { it[0] * 100.0 / 255 }
    val FUEL_RAIL_PRESSURE = Pid(0x23, "Fuel rail pressure", "kPa", 2) { (it[0] * 256 + it[1]) * 10.0 }
    val EGR_COMMANDED = Pid(0x2C, "Commanded EGR", "%", 1) { it[0] * 100.0 / 255 }
    val EGR_ERROR = Pid(0x2D, "EGR error", "%", 1) { it[0] * 100.0 / 128 - 100 }
    val FUEL_LEVEL = Pid(0x2F, "Fuel level", "%", 1) { it[0] * 100.0 / 255 }
    val BAROMETRIC = Pid(0x33, "Barometric pressure", "kPa", 1) { it[0].toDouble() }
    val MODULE_VOLTAGE = Pid(0x42, "Control module voltage", "V", 2) { (it[0] * 256 + it[1]) / 1000.0 }
    val AMBIENT_TEMP = Pid(0x46, "Ambient temperature", "°C", 1) { it[0] - 40.0 }
    val OIL_TEMP = Pid(0x5C, "Oil temperature", "°C", 1) { it[0] - 40.0 }
    val FUEL_RATE = Pid(0x5E, "Engine fuel rate", "L/h", 2) { (it[0] * 256 + it[1]) / 20.0 }

    val all: List<Pid> = listOf(
        ENGINE_LOAD, COOLANT_TEMP, FUEL_PRESSURE, MAP, RPM, SPEED, INTAKE_TEMP, MAF, THROTTLE,
        FUEL_RAIL_PRESSURE, EGR_COMMANDED, EGR_ERROR, FUEL_LEVEL, BAROMETRIC, MODULE_VOLTAGE,
        AMBIENT_TEMP, OIL_TEMP, FUEL_RATE,
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: Int): Pid? = byId[id]
}

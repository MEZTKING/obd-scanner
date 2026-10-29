package dev.obdscanner.core.obd

enum class DtcKind(val request: String, val responseService: Int) {
    STORED("03", 0x43),
    PENDING("07", 0x47),
    PERMANENT("0A", 0x4A),
}

data class Dtc(val code: String, val description: String?)

object DtcDecoder {
    /** Decodes one two-byte code, e.g. 0x01 0x33 -> P0133. */
    fun decode(a: Int, b: Int): String {
        val letter = "PCBU"[(a shr 6) and 0x03]
        val d1 = (a shr 4) and 0x03
        val d2 = a and 0x0F
        return "%c%d%X%02X".format(letter, d1, d2, b)
    }

    /** Parses the bytes following the response service byte; padding (0000) is skipped. */
    fun decodeAll(bytes: ByteArray, skipCountByte: Boolean): List<String> {
        var start = if (skipCountByte) 1 else 0
        val out = mutableListOf<String>()
        while (start + 1 < bytes.size) {
            val a = bytes[start].toInt() and 0xFF
            val b = bytes[start + 1].toInt() and 0xFF
            if (a != 0 || b != 0) out += decode(a, b)
            start += 2
        }
        return out
    }
}

/** Short descriptions for codes common on diesel VAG engines. Extend by adding entries. */
object DtcDatabase {
    private val descriptions = mapOf(
        "P0016" to "Crankshaft/camshaft position correlation",
        "P0087" to "Fuel rail pressure too low",
        "P0088" to "Fuel rail pressure too high",
        "P0100" to "Mass air flow circuit malfunction",
        "P0101" to "Mass air flow range/performance",
        "P0102" to "Mass air flow circuit low input",
        "P0103" to "Mass air flow circuit high input",
        "P0105" to "Manifold absolute pressure circuit malfunction",
        "P0110" to "Intake air temperature circuit malfunction",
        "P0115" to "Coolant temperature circuit malfunction",
        "P0190" to "Fuel rail pressure sensor circuit malfunction",
        "P0201" to "Injector circuit cylinder 1",
        "P0202" to "Injector circuit cylinder 2",
        "P0203" to "Injector circuit cylinder 3",
        "P0204" to "Injector circuit cylinder 4",
        "P0234" to "Turbocharger overboost condition",
        "P0243" to "Wastegate solenoid A malfunction",
        "P0299" to "Turbocharger underboost condition",
        "P0335" to "Crankshaft position sensor circuit malfunction",
        "P0340" to "Camshaft position sensor circuit malfunction",
        "P0380" to "Glow plug/heater circuit A malfunction",
        "P0401" to "EGR flow insufficient",
        "P0402" to "EGR flow excessive",
        "P0403" to "EGR circuit malfunction",
        "P0500" to "Vehicle speed sensor malfunction",
        "P0562" to "System voltage low",
        "P0563" to "System voltage high",
        "P0606" to "Control module processor fault",
        "P0670" to "Glow plug control module circuit malfunction",
    )

    fun describe(code: String): String? = descriptions[code]
}

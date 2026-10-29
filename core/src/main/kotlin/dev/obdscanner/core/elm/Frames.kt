package dev.obdscanner.core.elm

/**
 * Turns adapter text lines (headers off, spaces on or off) into response payloads.
 *
 * Single frame:  `41 0C 1A F8`  or `410C1AF8`
 * Multi frame:   `014`, `0: 49 02 01 57 41 55`, `1: 5A 5A 5A 38 58 37` ... (ISO-TP assembled by the adapter)
 *
 * Several ECUs answering the same request produce several payloads.
 */
object Frames {
    private val HEX = Regex("^[0-9A-Fa-f]+$")
    private val LENGTH_HEADER = Regex("^[0-9A-Fa-f]{3}$")
    private val FRAME_PREFIX = Regex("^([0-9A-Fa-f]):(.*)$")

    fun payloads(lines: List<String>): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            val next = lines.getOrNull(i + 1)?.trim()
            if (LENGTH_HEADER.matches(line) && next != null && FRAME_PREFIX.matches(next)) {
                val expected = line.toInt(16)
                val data = ArrayList<Byte>()
                i++
                while (i < lines.size) {
                    val m = FRAME_PREFIX.matchEntire(lines[i].trim()) ?: break
                    toBytes(m.groupValues[2])?.let { bytes -> bytes.forEach { data.add(it) } }
                    i++
                }
                result += data.take(expected).toByteArray()
            } else {
                toBytes(line)?.let { result += it }
                i++
            }
        }
        return result
    }

    fun toBytes(text: String): ByteArray? {
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty() || compact.length % 2 != 0 || !HEX.matches(compact)) return null
        return ByteArray(compact.length / 2) { compact.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

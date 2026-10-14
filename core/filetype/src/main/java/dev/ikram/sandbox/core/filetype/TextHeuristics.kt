package dev.ikram.sandbox.core.filetype

internal object TextHeuristics {
    /** Above this share of stray control bytes, it is binary data that happens to lack NULs. */
    private const val MAX_CONTROL_RATIO = 0.01

    fun hasUnicodeBom(head: ByteArray): Boolean = head.hasBytesAt(0, bytesOf(0xEF, 0xBB, 0xBF)) ||
        head.hasBytesAt(0, bytesOf(0xFF, 0xFE)) ||
        head.hasBytesAt(0, bytesOf(0xFE, 0xFF))

    /**
     * True when [head] reads as text in UTF-8 or a single byte encoding (the text viewer
     * falls back to Windows-1252). Binary formats almost always contain NUL bytes or other
     * control characters within the first few kilobytes.
     */
    fun looksLikeText(head: ByteArray): Boolean {
        if (head.isEmpty()) return false
        var controls = 0
        for (byte in head) {
            val value = byte.toInt() and 0xFF
            if (value == 0) return false
            if (isStrayControl(value)) controls++
        }
        return controls <= head.size * MAX_CONTROL_RATIO
    }

    /** Tab, line feed, vertical tab, form feed, carriage return and escape (coloured logs) are normal in text. */
    private fun isStrayControl(value: Int): Boolean = when (value) {
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x1B -> false
        else -> value < 0x20 || value == 0x7F
    }
}

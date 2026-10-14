package dev.ikram.sandbox.core.filetype

// Small unsigned readers for parsing headers. Every function assumes the caller has
// already checked the array is long enough.

internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

internal fun ByteArray.u16le(index: Int): Int = u8(index) or (u8(index + 1) shl 8)

internal fun ByteArray.u16be(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

internal fun ByteArray.u32le(index: Int): Long = (u16le(index).toLong()) or (u16le(index + 2).toLong() shl 16)

internal fun ByteArray.u32be(index: Int): Long = (u16be(index).toLong() shl 16) or u16be(index + 2).toLong()

/** Signed, as zip64 stores it. Values past [Long.MAX_VALUE] come back negative and are rejected by callers. */
internal fun ByteArray.u64le(index: Int): Long = u32le(index) or (u32le(index + 4) shl 32)

internal fun ByteArray.hasBytesAt(index: Int, expected: ByteArray): Boolean {
    if (index < 0 || index + expected.size > size) return false
    return expected.indices.all { this[index + it] == expected[it] }
}

internal fun ByteArray.hasAsciiAt(index: Int, expected: String): Boolean =
    hasBytesAt(index, expected.toByteArray(Charsets.US_ASCII))

internal fun ByteArray.asciiAt(index: Int, length: Int): String =
    if (index + length > size) "" else String(this, index, length, Charsets.ISO_8859_1)

internal fun bytesOf(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

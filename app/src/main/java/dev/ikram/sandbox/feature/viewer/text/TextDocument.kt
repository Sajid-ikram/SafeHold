package dev.ikram.sandbox.feature.viewer.text

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * A text file laid out for the viewer. The whole (cleaned) text is one string with lines
 * joined by '\n'; a row is a line, or a slice of a very long one. Rows are described by
 * offsets rather than objects, so a file of a million short lines stays small in memory.
 */
class TextDocument internal constructor(
    /** What the viewer shows: tabs expanded, hidden characters made visible, lines joined by '\n'. */
    val text: String,
    private val starts: IntArray,
    private val ends: IntArray,
    /** Line number of each row, or 0 for a row that continues the line above. */
    private val lineNumbers: IntArray,
    val lineCount: Int,
    /** The widest row in monospace columns (wide CJK characters count as two). */
    val widestRow: Int,
    val encoding: String,
    /** Only the start of the file is shown: it is over the size or line limit. */
    val truncated: Boolean,
) {
    val rowCount: Int get() = starts.size

    fun row(index: Int): String = text.substring(starts[index], ends[index])

    fun rowStart(index: Int): Int = starts[index]

    fun rowEnd(index: Int): Int = ends[index]

    /** Null for rows that continue a long line. */
    fun lineNumber(index: Int): Int? = lineNumbers[index].takeIf { it > 0 }

    /** The row holding [offset] in [text]. */
    fun rowAt(offset: Int): Int {
        val found = starts.binarySearch(offset)
        return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
    }
}

/** Decoded text before it is laid out, kept as it is for safe copies. */
data class DecodedText(val text: String, val encoding: String)

/**
 * Turns a text file's bytes into text, never trusting the server's charset: a byte order
 * mark if there is one, else strict UTF-8, else Windows-1252 (which maps nearly every
 * byte). The sniffer only calls a file text after checking it reads in one of these.
 */
object TextDecoder {
    /** The viewer shows at most this much of a file. */
    const val MAX_BYTES = 5 * 1024 * 1024

    /** And at most this many rows, so a file of nothing but line breaks stays cheap. */
    const val MAX_ROWS = 500_000

    /** Long lines are cut into rows of this many characters, so no single row is huge to draw. */
    const val ROW_CHARS = 1_000

    private const val TAB_WIDTH = 4
    private const val REPLACEMENT = '\uFFFD'
    private val ANSI_ESCAPE = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    private val UTF_8_BOM = bytesOf(0xEF, 0xBB, 0xBF)
    private val UTF_16LE_BOM = bytesOf(0xFF, 0xFE)
    private val UTF_16BE_BOM = bytesOf(0xFE, 0xFF)

    /** [complete] is false when [bytes] stops partway through the file, maybe mid-character. */
    fun decode(bytes: ByteArray, complete: Boolean = true): DecodedText = when {
        bytes.startsWith(UTF_8_BOM) ->
            DecodedText(lenient(bytes, UTF_8_BOM.size, Charsets.UTF_8, complete), "UTF-8")

        bytes.startsWith(UTF_16LE_BOM) ->
            DecodedText(lenient(bytes, UTF_16LE_BOM.size, Charsets.UTF_16LE, complete), "UTF-16")

        bytes.startsWith(UTF_16BE_BOM) ->
            DecodedText(lenient(bytes, UTF_16BE_BOM.size, Charsets.UTF_16BE, complete), "UTF-16")

        else -> strict(bytes, Charsets.UTF_8, complete)?.let { DecodedText(it, "UTF-8") }
            ?: DecodedText(lenient(bytes, 0, WINDOWS_1252, complete), "Windows-1252")
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun bytesOf(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    fun layout(decoded: DecodedText, truncated: Boolean): TextDocument {
        val out = StringBuilder(decoded.text.length)
        val starts = IntList()
        val ends = IntList()
        val numbers = IntList()
        var lineCount = 0
        var widest = 0
        var cut = truncated
        for (line in lines(decoded.text)) {
            if (starts.size >= MAX_ROWS) {
                cut = true
                break
            }
            lineCount++
            if (out.isNotEmpty()) out.append('\n')
            val lineStart = out.length
            appendClean(line, out)
            var rowStart = lineStart
            do {
                var rowEnd = minOf(rowStart + ROW_CHARS, out.length)
                // Never split a surrogate pair across rows.
                if (rowEnd < out.length && out[rowEnd - 1].isHighSurrogate()) rowEnd--
                starts.add(rowStart)
                ends.add(rowEnd)
                numbers.add(if (rowStart == lineStart) lineCount else 0)
                widest = maxOf(widest, columns(out, rowStart, rowEnd))
                rowStart = rowEnd
            } while (rowStart < out.length)
        }
        return TextDocument(
            text = out.toString(),
            starts = starts.toArray(),
            ends = ends.toArray(),
            lineNumbers = numbers.toArray(),
            lineCount = lineCount,
            widestRow = widest,
            encoding = decoded.encoding,
            truncated = cut,
        )
    }

    /** Splits on \r\n, \n and \r. A final line break does not start another, empty, line. */
    internal fun lines(text: String): Sequence<String> = sequence {
        var start = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\n' || char == '\r') {
                yield(text.substring(start, index))
                if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                start = index + 1
            }
            index++
        }
        if (start < text.length || text.isEmpty()) yield(text.substring(start))
    }

    /**
     * Expands tabs, drops terminal colour codes, and shows control characters and
     * direction overrides as the replacement mark. Overrides could otherwise make the
     * text read differently from what it really says.
     */
    private fun appendClean(line: String, out: StringBuilder) {
        val text = if ('\u001B' in line) ANSI_ESCAPE.replace(line, "") else line
        var column = 0
        for (char in text) {
            when {
                char == '\t' -> {
                    val spaces = TAB_WIDTH - column % TAB_WIDTH
                    repeat(spaces) { out.append(' ') }
                    column += spaces
                }

                isHidden(char) -> {
                    out.append(REPLACEMENT)
                    column++
                }

                else -> {
                    out.append(char)
                    column++
                }
            }
        }
    }

    private fun isHidden(char: Char): Boolean = char < ' ' ||
        char in '\u007F'..'\u009F' ||
        char in '\u202A'..'\u202E' ||
        char in '\u2066'..'\u2069'

    private fun columns(text: CharSequence, start: Int, end: Int): Int {
        var count = 0
        for (index in start until end) {
            val char = text[index]
            count += when {
                char.isLowSurrogate() -> 0
                char >= '\u1100' && isWide(char) -> 2
                else -> 1
            }
        }
        return count
    }

    /** East Asian wide and full width ranges, roughly: enough to size a row, not to typeset it. */
    private fun isWide(char: Char): Boolean = char in '\u1100'..'\u115F' ||
        char in '\u2E80'..'\uA4CF' ||
        char in '\uAC00'..'\uD7A3' ||
        char in '\uF900'..'\uFAFF' ||
        char in '\uFE30'..'\uFE4F' ||
        char in '\uFF00'..'\uFF60' ||
        char in '\uFFE0'..'\uFFE6' ||
        char.isHighSurrogate()

    /** Null at the first invalid sequence. */
    private fun strict(bytes: ByteArray, charset: Charset, complete: Boolean): String? =
        convert(bytes, 0, charset, complete, CodingErrorAction.REPORT)

    /** Invalid bytes become the replacement mark. */
    private fun lenient(bytes: ByteArray, offset: Int, charset: Charset, complete: Boolean): String =
        convert(bytes, offset, charset, complete, CodingErrorAction.REPLACE).orEmpty()

    /** An incomplete final character is dropped when the bytes stop partway through the file. */
    private fun convert(
        bytes: ByteArray,
        offset: Int,
        charset: Charset,
        complete: Boolean,
        action: CodingErrorAction,
    ): String? {
        val decoder = charset.newDecoder().onMalformedInput(action).onUnmappableCharacter(action)
        val input = ByteBuffer.wrap(bytes, offset, bytes.size - offset)
        val output = CharBuffer.allocate((input.remaining() * decoder.maxCharsPerByte().toDouble()).toInt() + 1)
        if (decoder.decode(input, output, complete).isError) return null
        if (complete && decoder.flush(output).isError) return null
        output.flip()
        return output.toString()
    }
}

/** Where a search term was found in [TextDocument.text]. */
data class TextMatch(val start: Int, val end: Int)

object TextSearch {
    /** Past this, the count reads "1000+" and the rest are not highlighted. */
    const val MAX_MATCHES = 1_000

    /** Case-insensitive, in order. [checkCancelled] runs between matches so a newer search can stop this one. */
    fun find(text: String, query: String, checkCancelled: () -> Unit = {}): List<TextMatch> {
        if (query.isEmpty()) return emptyList()
        val matches = ArrayList<TextMatch>()
        var from = 0
        while (matches.size < MAX_MATCHES) {
            checkCancelled()
            val index = text.indexOf(query, from, ignoreCase = true)
            if (index < 0) break
            matches.add(TextMatch(index, index + query.length))
            from = index + query.length
        }
        return matches
    }

    /** The parts of row [row] covered by [matches], with whether each is the current match. */
    fun highlights(document: TextDocument, row: Int, matches: List<TextMatch>, current: Int): List<Highlight> {
        if (matches.isEmpty()) return emptyList()
        val start = document.rowStart(row)
        val end = document.rowEnd(row)
        var index = matches.binarySearch { if (it.end <= start) -1 else 1 }.let { -it - 1 }
        val found = ArrayList<Highlight>()
        while (index < matches.size && matches[index].start < end) {
            val match = matches[index]
            found.add(Highlight(maxOf(match.start, start) - start, minOf(match.end, end) - start, index == current))
            index++
        }
        return found
    }

    /** [start] and [end] are within the row's own text. */
    data class Highlight(val start: Int, val end: Int, val isCurrent: Boolean)
}

/** A growable int array: rows are counted in the hundreds of thousands, so no boxing. */
private class IntList {
    private var values = IntArray(INITIAL)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = value
    }

    fun toArray(): IntArray = values.copyOf(size)

    private companion object {
        const val INITIAL = 256
    }
}

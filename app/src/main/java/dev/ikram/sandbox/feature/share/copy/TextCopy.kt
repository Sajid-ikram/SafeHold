package dev.ikram.sandbox.feature.share.copy

import dev.ikram.sandbox.core.data.di.IoDispatcher
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.share.ShareNames
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Text rewritten as clean UTF-8: the words survive, invisible characters do not. */
class TextCopy @Inject constructor(
    private val files: ShareFiles,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun copy(file: VaultFile): Flow<CopyProgress> = flow {
        emit(CopyProgress.Working(null))
        val context = currentCoroutineContext()
        val source = files.source(file)
        val share = files.writing(DetectedType.TEXT.extension) { target ->
            TextTranscoder.transcode(
                open = { source.inputStream() },
                create = { target.outputStream() },
                checkCancelled = { context.ensureActive() },
            )
            PreparedShare(target, ShareNames.forShare(file.displayName, DetectedType.TEXT), TEXT_MIME)
        }
        emit(CopyProgress.Done(share))
    }.flowOn(io)

    private companion object {
        const val TEXT_MIME = "text/plain; charset=utf-8"
    }
}

/**
 * Reads text the way the viewer does (byte order mark, else strict UTF-8, else
 * Windows-1252) and writes it as UTF-8 without a byte order mark. Control characters,
 * direction overrides and terminal colour codes are dropped; tabs, line breaks and form
 * feeds are kept. Streams, so a file of any size takes little memory.
 */
object TextTranscoder {
    private const val CHUNK = 64 * 1024
    private const val ESCAPE = '\u001B'
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
    private val UTF_8_BOM = bytesOf(0xEF, 0xBB, 0xBF)

    /** Longest first, so no mark is mistaken for the start of another. */
    private val BYTE_ORDER_MARKS: Map<ByteArray, Charset> = linkedMapOf(
        UTF_8_BOM to Charsets.UTF_8,
        bytesOf(0xFF, 0xFE) to Charsets.UTF_16LE,
        bytesOf(0xFE, 0xFF) to Charsets.UTF_16BE,
    )

    private fun bytesOf(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** [open] and [create] may be called twice: once more if the text turns out not to be UTF-8. */
    fun transcode(open: () -> InputStream, create: () -> OutputStream, checkCancelled: () -> Unit = {}) {
        val (charset, skip) = open().use(::byteOrderMark)
        if (charset != null) {
            copy(open, create, charset, skip, CodingErrorAction.REPLACE, checkCancelled)
            return
        }
        try {
            copy(open, create, Charsets.UTF_8, 0, CodingErrorAction.REPORT, checkCancelled)
        } catch (@Suppress("SwallowedException") notUtf8: CharacterCodingException) {
            copy(open, create, WINDOWS_1252, 0, CodingErrorAction.REPLACE, checkCancelled)
        }
    }

    /** The charset a byte order mark announces, and the mark's length; null when there is none. */
    private fun byteOrderMark(input: InputStream): Pair<Charset?, Long> {
        val head = ByteArray(UTF_8_BOM.size)
        val read = BufferedInputStream(input).read(head).coerceAtLeast(0)
        val found = BYTE_ORDER_MARKS.entries.firstOrNull { (mark, _) ->
            read >= mark.size && mark.indices.all { head[it] == mark[it] }
        }
        return found?.let { (mark, charset) -> charset to mark.size.toLong() } ?: (null to 0L)
    }

    private fun copy(
        open: () -> InputStream,
        create: () -> OutputStream,
        charset: Charset,
        skip: Long,
        onError: CodingErrorAction,
        checkCancelled: () -> Unit,
    ) {
        val decoder = charset.newDecoder().onMalformedInput(onError).onUnmappableCharacter(onError)
        open().use { input ->
            input.skipFully(skip)
            InputStreamReader(input, decoder).use { reader ->
                create().bufferedWriter(Charsets.UTF_8).use { writer -> clean(reader, writer, checkCancelled) }
            }
        }
    }

    /** Copies [reader] to [writer], leaving out what a reader of the text should never meet. */
    fun clean(reader: Reader, writer: Writer, checkCancelled: () -> Unit = {}) {
        val buffer = CharArray(CHUNK)
        var state = State.TEXT
        while (true) {
            checkCancelled()
            val count = reader.read(buffer)
            if (count < 0) break
            for (index in 0 until count) state = next(state, buffer[index], writer)
        }
    }

    private fun next(state: State, char: Char, writer: Writer): State = when (state) {
        State.TEXT -> when {
            char == ESCAPE -> State.ESCAPE
            isKept(char) -> State.TEXT.also { writer.write(char.code) }
            else -> State.TEXT
        }

        // An escape and the character after it go; "[" starts a longer sequence.
        State.ESCAPE -> if (char == '[') State.SEQUENCE else State.TEXT

        // Parameters run until a final character from @ to ~.
        State.SEQUENCE -> if (char in '@'..'~') State.TEXT else State.SEQUENCE
    }

    private fun isKept(char: Char): Boolean = when {
        char == '\t' || char == '\n' || char == '\r' || char == '\u000C' -> true
        char < ' ' || char in '\u007F'..'\u009F' -> false
        char in '\u202A'..'\u202E' || char in '\u2066'..'\u2069' || char == '\uFEFF' -> false
        else -> true
    }

    private fun InputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) return
                left--
            } else {
                left -= skipped
            }
        }
    }

    private enum class State { TEXT, ESCAPE, SEQUENCE }
}

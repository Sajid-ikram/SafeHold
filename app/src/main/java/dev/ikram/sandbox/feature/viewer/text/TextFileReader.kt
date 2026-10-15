package dev.ikram.sandbox.feature.viewer.text

import dev.ikram.sandbox.core.data.di.IoDispatcher
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.VaultFile
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Reads the start of a text file from the vault. Text is only ever treated as plain
 * characters (no markup, links or scripts), so it is read here in the app process.
 */
class TextFileReader @Inject constructor(
    private val storage: VaultStorage,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Null when the file is gone or cannot be read. */
    suspend fun read(file: VaultFile): TextDocument? = withContext(io) {
        val source = storage.fileFor(file.id)
        try {
            val size = source.length()
            val bytes = readStart(source, TextDecoder.MAX_BYTES)
            val complete = size <= TextDecoder.MAX_BYTES
            TextDecoder.layout(TextDecoder.decode(bytes, complete), truncated = !complete)
        } catch (@Suppress("SwallowedException") failed: IOException) {
            null
        }
    }

    private fun readStart(file: File, limit: Int): ByteArray = file.inputStream().use { input ->
        val buffer = ByteArray(minOf(file.length(), limit.toLong()).toInt())
        var read = 0
        while (read < buffer.size) {
            val count = input.read(buffer, read, buffer.size - read)
            if (count < 0) break
            read += count
        }
        if (read == buffer.size) buffer else buffer.copyOf(read)
    }
}

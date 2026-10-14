package dev.ikram.sandbox.core.filetype

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Random read access to a file's bytes. The sniffer needs more than the first few bytes:
 * ZIP based formats keep their table of contents at the end of the file.
 */
interface ByteSource {
    val size: Long

    /** Reads up to [length] bytes at [position]. Returns how many were read, or -1 past the end. */
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/** Exactly [length] bytes at [position], or null if the source is too short or [position] is negative. */
fun ByteSource.readFully(position: Long, length: Int): ByteArray? {
    if (position < 0 || length < 0 || position + length > size) return null
    val buffer = ByteArray(length)
    var done = 0
    while (done < length) {
        val read = readAt(position + done, buffer, done, length - done)
        if (read <= 0) return null
        done += read
    }
    return buffer
}

/** Up to [max] bytes from the start of the source. */
fun ByteSource.readHead(max: Int): ByteArray = readFully(0, minOf(size, max.toLong()).toInt()) ?: ByteArray(0)

class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, count)
        return count
    }
}

/** Reads a file through a [FileChannel], so positional reads never move a shared file pointer. */
class FileByteSource private constructor(private val file: RandomAccessFile) :
    ByteSource,
    Closeable {

    private val channel: FileChannel = file.channel

    override val size: Long = channel.size()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int =
        channel.read(ByteBuffer.wrap(buffer, offset, length), position)

    override fun close() = file.close()

    companion object {
        fun open(file: File): FileByteSource = FileByteSource(RandomAccessFile(file, "r"))
    }
}

package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The same detection through a real file on disk, as the downloader will use it. */
class FileByteSourceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun sniffsFromDisk() {
        val file = folder.newFile("download.bin").apply { writeBytes(Fixtures.jpeg() + Fixtures.apk()) }
        val type = FileByteSource.open(file).use { MagicSniffer.sniff(it) }
        assertEquals(DetectedType.APK, type)
    }

    @Test
    fun readFullyRejectsOutOfRangeReads() {
        val source = ByteArraySource(ByteArray(10))
        assertEquals(null, source.readFully(8, 4))
        assertEquals(null, source.readFully(-1, 1))
        assertEquals(4, source.readFully(6, 4)?.size)
    }
}

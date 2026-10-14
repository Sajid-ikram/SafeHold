package dev.ikram.sandbox.core.filetype

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Test files. Images and WAV are real files written by the JDK's encoders, ZIPs are real
 * archives from [ZipOutputStream]; everything else is a minimal but valid header.
 */
object Fixtures {
    fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    fun ascii(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)

    /** [prefix] followed by [filler] up to [size] bytes, so headers look like real files. */
    fun padded(prefix: ByteArray, size: Int = 1024, filler: Int = 0x00): ByteArray =
        prefix + ByteArray(maxOf(0, size - prefix.size)) { filler.toByte() }

    // Images

    fun png() = imageIo("png")

    fun jpeg() = imageIo("jpeg")

    fun gif() = imageIo("gif")

    fun bmp() = imageIo("bmp")

    private fun imageIo(format: String): ByteArray {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        image.setRGB(3, 3, 0x0B6B62)
        return ByteArrayOutputStream().also { check(ImageIO.write(image, format, it)) }.toByteArray()
    }

    fun webp() = riff("WEBP", ascii("VP8 ") + le32(10) + ByteArray(10))

    fun ico(): ByteArray {
        val header = bytes(0, 0, 1, 0, 1, 0)
        val entry = bytes(16, 16, 0, 0, 1, 0, 32, 0) + le32(1128) + le32(22)
        return padded(header + entry, 256)
    }

    /** ISO base media file: an `ftyp` box with [major] and [compatible] brands, then `mdat`. */
    fun isoBmff(major: String, vararg compatible: String): ByteArray {
        val ftypBody = ascii(major) + be32(0) + compatible.fold(ByteArray(0)) { acc, b -> acc + ascii(b) }
        val ftyp = be32(8 + ftypBody.size) + ascii("ftyp") + ftypBody
        return ftyp + be32(16) + ascii("mdat") + ByteArray(8)
    }

    // Video and audio

    fun ebml(docType: String): ByteArray {
        val docTypeElement = bytes(0x42, 0x82, 0x80 or docType.length) + ascii(docType)
        return padded(bytes(0x1A, 0x45, 0xDF, 0xA3, 0x80 or docTypeElement.size) + docTypeElement, 256)
    }

    fun avi() = riff("AVI ", ascii("LIST") + le32(4) + ascii("hdrl"))

    fun flv() = padded(ascii("FLV") + bytes(0x01, 0x05, 0, 0, 0, 9), 64)

    fun mpegTs(): ByteArray = ByteArray(188 * 4).also { ts -> (0 until 4).forEach { ts[it * 188] = 0x47 } }

    fun wav(): ByteArray {
        val format = AudioFormat(8000f, 16, 1, true, false)
        val stream = AudioInputStream(ByteArrayInputStream(ByteArray(1600)), format, 800)
        return ByteArrayOutputStream().also { AudioSystem.write(stream, AudioFileFormat.Type.WAVE, it) }.toByteArray()
    }

    fun ogg() = padded(ascii("OggS") + bytes(0, 2) + ByteArray(22) + bytes(1) + ascii("vorbis"), 128)

    fun flac() = padded(ascii("fLaC") + bytes(0, 0, 0, 34), 128)

    /** One MPEG-1 Layer III frame header, 128 kbps, 44.1 kHz. */
    fun mp3Frame() = padded(bytes(0xFF, 0xFB, 0x90, 0x64), 417)

    fun adtsFrame() = padded(bytes(0xFF, 0xF1, 0x50, 0x80, 0x02, 0x1F, 0xFC), 128)

    /** An ID3v2 tag of [tagSize] bytes in front of [audio]. */
    fun id3(audio: ByteArray, tagSize: Int = 64): ByteArray {
        val size = bytes(
            (tagSize shr 21) and 0x7F,
            (tagSize shr 14) and 0x7F,
            (tagSize shr 7) and 0x7F,
            tagSize and 0x7F,
        )
        return ascii("ID3") + bytes(4, 0, 0) + size + ByteArray(tagSize) + audio
    }

    fun amr() = padded(ascii("#!AMR\n"), 64)

    // Documents and archives

    fun pdf() = ascii("%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n")

    fun gzip() = padded(bytes(0x1F, 0x8B, 0x08, 0x00), 64)

    fun bzip2() = padded(ascii("BZh9") + bytes(0x31, 0x41, 0x59, 0x26, 0x53, 0x59), 64)

    fun xz() = padded(bytes(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00), 64)

    fun sevenZip() = padded(bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C, 0x00, 0x04), 64)

    fun rar() = padded(ascii("Rar!") + bytes(0x1A, 0x07, 0x01, 0x00), 64)

    fun zstd() = padded(bytes(0x28, 0xB5, 0x2F, 0xFD), 64)

    fun cab() = padded(ascii("MSCF") + bytes(0, 0, 0, 0), 64)

    fun tar(): ByteArray = ByteArray(1024).also { ascii("ustar\u000000").copyInto(it, 257) }

    /** A real ZIP with one small stored entry per name. */
    fun zip(vararg names: String, comment: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            comment?.let(zip::setComment)
            names.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("content of $name".toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun apk() = zip("AndroidManifest.xml", "classes.dex", "resources.arsc", "res/drawable/icon.png")

    /**
     * Rewrites a plain ZIP's end record to the ZIP64 form: the classic record holds only
     * 0xFFFF placeholders and the real values live in a ZIP64 record found via a locator.
     */
    fun toZip64(zip: ByteArray): ByteArray {
        val eocd = zip.size - 22
        val buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
        val entries = buffer.getShort(eocd + 10).toLong() and 0xFFFF
        val cdSize = buffer.getInt(eocd + 12).toLong() and 0xFFFFFFFFL
        val cdOffset = buffer.getInt(eocd + 16).toLong() and 0xFFFFFFFFL

        val record = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x06064b50).putLong(44).putShort(45).putShort(45).putInt(0).putInt(0)
            .putLong(entries).putLong(entries).putLong(cdSize).putLong(cdOffset)
            .array()
        val locator = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x07064b50).putInt(0).putLong(eocd.toLong()).putInt(1)
            .array()
        val end = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x06054b50).putShort(0).putShort(0)
            .putShort(-1).putShort(-1).putInt(-1).putInt(-1).putShort(0)
            .array()
        return zip.copyOfRange(0, eocd) + record + locator + end
    }

    // Executables

    fun peExe(): ByteArray = ByteArray(512).also { exe ->
        ascii("MZ").copyInto(exe)
        le32(0x80).copyInto(exe, 0x3C)
        ascii("PE\u0000\u0000").copyInto(exe, 0x80)
    }

    fun elf() = padded(bytes(0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00), 128)

    fun machO() = padded(bytes(0xCF, 0xFA, 0xED, 0xFE, 0x07, 0x00, 0x00, 0x01), 128)

    fun fatMachO() = padded(bytes(0xCA, 0xFE, 0xBA, 0xBE, 0x00, 0x00, 0x00, 0x02), 128)

    /** Java 17 class file: minor 0, major 61. */
    fun javaClass() = padded(bytes(0xCA, 0xFE, 0xBA, 0xBE, 0x00, 0x00, 0x00, 0x3D), 128)

    fun dex() = padded(ascii("dex\n035") + bytes(0), 112)

    fun lnk() = padded(bytes(0x4C, 0x00, 0x00, 0x00, 0x01, 0x14, 0x02, 0x00), 76)

    fun crx() = padded(ascii("Cr24") + bytes(3, 0, 0, 0), 64)

    fun rpm() = padded(bytes(0xED, 0xAB, 0xEE, 0xDB, 0x03, 0x00), 96)

    fun deb() = padded(ascii("!<arch>\ndebian-binary   1342943816  0     0     100644  4         `\n2.0\n"), 128)

    /**
     * A minimal OLE compound file: 512 byte header with the first directory sector at 0,
     * so the root entry (and its class ID at byte 80) sits at offset 512.
     */
    fun ole(rootClsid: ByteArray): ByteArray {
        val file = ByteArray(1024)
        bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1).copyInto(file)
        bytes(9, 0).copyInto(file, 30)
        le32(0).copyInto(file, 48)
        rootClsid.copyInto(file, 512 + 80)
        return file
    }

    val MSI_CLSID = bytes(0x84, 0x10, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC0, 0, 0, 0, 0, 0, 0, 0x46)
    val WORD_CLSID = bytes(0x06, 0x09, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC0, 0, 0, 0, 0, 0, 0, 0x46)

    // Helpers

    private fun riff(form: String, body: ByteArray): ByteArray =
        ascii("RIFF") + le32(4 + body.size) + ascii(form) + body

    fun le32(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    fun be32(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
}

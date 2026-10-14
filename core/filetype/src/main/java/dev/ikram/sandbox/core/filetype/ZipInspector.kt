package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType

/**
 * Reads a ZIP's central directory (its table of contents, at the end of the file) and
 * decides what kind of ZIP it is from the entry names. APKs, JARs, app bundles and
 * macro-enabled Office files are all ZIPs; only the entry list tells them apart.
 *
 * Mirrors what installers do: find the last end-of-central-directory record, then walk
 * the central directory. Anything we cannot parse returns null, and callers treat that
 * as unknown rather than guessing.
 */
internal object ZipInspector {
    private const val EOCD_SIGNATURE = 0x06054b50L
    private const val EOCD_SIZE = 22
    private const val MAX_COMMENT_LENGTH = 0xFFFF
    private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50L
    private const val ZIP64_LOCATOR_SIZE = 20
    private const val ZIP64_EOCD_SIGNATURE = 0x06064b50L
    private const val ZIP64_EOCD_SIZE = 56
    private const val CENTRAL_HEADER_SIGNATURE = 0x02014b50L
    private const val CENTRAL_HEADER_SIZE = 46
    private const val UTF8_NAME_FLAG = 1 shl 11

    /** Bounds the work for hostile files. Real APK directories are a few MB at most. */
    private const val MAX_CENTRAL_DIRECTORY_BYTES = 64L * 1024 * 1024
    private const val READ_WINDOW = 1 shl 20

    /** ZIP types that carry code a device could install or run. */
    val EXECUTABLE_TYPES = setOf(
        DetectedType.APK,
        DetectedType.AAB,
        DetectedType.APKS,
        DetectedType.XAPK,
        DetectedType.DEX,
        DetectedType.JAR,
        DetectedType.APPX,
        DetectedType.IPA,
    )

    fun inspect(source: ByteSource): DetectedType? {
        val end = findEndRecord(source) ?: return null
        val directory = locateDirectory(source, end) ?: return null
        val contents = ZipContents()
        return if (readEntries(source, directory, contents)) contents.classify() else null
    }

    private class EndRecord(val position: Long, val entries: Long, val directorySize: Long, val directoryOffset: Long)

    private class Directory(val start: Long, val size: Long)

    private fun findEndRecord(source: ByteSource): EndRecord? {
        if (source.size < EOCD_SIZE) return null
        val tailLength = minOf(source.size, (EOCD_SIZE + MAX_COMMENT_LENGTH).toLong()).toInt()
        val tailStart = source.size - tailLength
        val tail = source.readFully(tailStart, tailLength) ?: return null
        // Search backwards from the end, as installers do: the first signature whose
        // declared comment fits inside the file is the record.
        val at = (tailLength - EOCD_SIZE downTo 0).firstOrNull { i ->
            tail.u32le(i) == EOCD_SIGNATURE && i + EOCD_SIZE + tail.u16le(i + 20) <= tailLength
        } ?: return null
        return EndRecord(
            position = tailStart + at,
            entries = tail.u16le(at + 10).toLong(),
            directorySize = tail.u32le(at + 12),
            directoryOffset = tail.u32le(at + 16),
        )
    }

    private fun locateDirectory(source: ByteSource, end: EndRecord): Directory? {
        val usesZip64 = end.entries == 0xFFFFL || end.directorySize == 0xFFFFFFFFL ||
            end.directoryOffset == 0xFFFFFFFFL
        val record = if (usesZip64) readZip64Record(source, end) ?: return null else end
        val size = record.directorySize
        if (size < 0 || size > MAX_CENTRAL_DIRECTORY_BYTES || record.directoryOffset < 0) return null
        if (size == 0L) return Directory(record.position, 0)

        // Data prepended to a ZIP (self extractors, polyglots) shifts every offset by the
        // same amount, so also try the directory where it physically has to be.
        val declared = record.directoryOffset
        val physical = record.position - size
        return listOf(declared, physical)
            .firstOrNull { start -> start >= 0 && start + size <= record.position && hasCentralHeaderAt(source, start) }
            ?.let { Directory(it, size) }
    }

    private fun readZip64Record(source: ByteSource, end: EndRecord): EndRecord? {
        val locator = source.readFully(end.position - ZIP64_LOCATOR_SIZE, ZIP64_LOCATOR_SIZE) ?: return null
        if (locator.u32le(0) != ZIP64_LOCATOR_SIGNATURE) return null
        val declared = locator.u64le(8)
        val physical = end.position - ZIP64_LOCATOR_SIZE - ZIP64_EOCD_SIZE
        val position = listOf(declared, physical).firstOrNull { position ->
            source.readFully(position, 4)?.u32le(0) == ZIP64_EOCD_SIGNATURE
        } ?: return null
        val record = source.readFully(position, ZIP64_EOCD_SIZE) ?: return null
        return EndRecord(
            position = position,
            entries = record.u64le(32),
            directorySize = record.u64le(40),
            directoryOffset = record.u64le(48),
        )
    }

    private fun hasCentralHeaderAt(source: ByteSource, position: Long): Boolean =
        source.readFully(position, 4)?.u32le(0) == CENTRAL_HEADER_SIGNATURE

    /** Feeds every entry name to [contents]. Returns false if the directory is malformed. */
    private fun readEntries(source: ByteSource, directory: Directory, contents: ZipContents): Boolean {
        val reader = WindowReader(source, directory.start + directory.size)
        var position = directory.start
        val end = directory.start + directory.size
        while (position < end) {
            val header = reader.read(position, CENTRAL_HEADER_SIZE) ?: return false
            if (header.u32le(0) != CENTRAL_HEADER_SIGNATURE) return false
            val nameLength = header.u16le(28)
            val extraLength = header.u16le(30)
            val commentLength = header.u16le(32)
            val nameBytes = reader.read(position + CENTRAL_HEADER_SIZE, nameLength) ?: return false
            val charset = if (header.u16le(8) and UTF8_NAME_FLAG != 0) Charsets.UTF_8 else Charsets.ISO_8859_1
            contents.add(String(nameBytes, charset))
            position += CENTRAL_HEADER_SIZE + nameLength + extraLength + commentLength
        }
        return position == end
    }

    /** Buffered positional reads over the directory, so a large one costs few I/O calls. */
    private class WindowReader(private val source: ByteSource, private val limit: Long) {
        private var window = ByteArray(0)
        private var windowStart = 0L

        fun read(position: Long, length: Int): ByteArray? {
            if (position + length > limit) return null
            if (position < windowStart || position + length > windowStart + window.size) {
                val windowLength = minOf(maxOf(READ_WINDOW, length).toLong(), limit - position).toInt()
                window = source.readFully(position, windowLength) ?: return null
                windowStart = position
            }
            val from = (position - windowStart).toInt()
            return window.copyOfRange(from, from + length)
        }
    }
}

/** Facts about a ZIP's entry names, gathered in one pass. */
internal class ZipContents {
    private var androidManifest = false
    private var bundleManifest = false
    private var apkSetToc = false
    private var nestedApk = false
    private var dex = false
    private var classFile = false
    private var jarManifest = false
    private var appxManifest = false
    private var iosPayload = false
    private var vbaProject = false
    private var odfMacros = false
    private var ooxmlContentTypes = false
    private var wordPart = false
    private var excelPart = false
    private var powerPointPart = false
    private var mimetype = false
    private var epubContainer = false
    private var odfManifest = false

    fun add(rawName: String) {
        // Lower-cased and normalised, so "./ANDROIDMANIFEST.XML" cannot slip past.
        val name = rawName.replace('\\', '/').trimStart('/').removePrefix("./").lowercase()
        when (name) {
            "androidmanifest.xml" -> androidManifest = true
            "base/manifest/androidmanifest.xml" -> bundleManifest = true
            "toc.pb" -> apkSetToc = true
            "meta-inf/manifest.mf" -> jarManifest = true
            "appxmanifest.xml", "appxmetadata/appxbundlemanifest.xml" -> appxManifest = true
            "[content_types].xml" -> ooxmlContentTypes = true
            "mimetype" -> mimetype = true
            "meta-inf/container.xml" -> epubContainer = true
            "meta-inf/manifest.xml" -> odfManifest = true
        }
        if (NESTED_APP_EXTENSIONS.any(name::endsWith)) nestedApk = true
        if (name.endsWith(".dex")) dex = true
        if (name.endsWith(".class")) classFile = true
        if (name.startsWith("payload/") && name.contains(".app/")) iosPayload = true
        if (name.endsWith("vbaproject.bin")) vbaProject = true
        if (name.startsWith("basic/")) odfMacros = true
        if (name.startsWith("word/")) wordPart = true
        if (name.startsWith("xl/")) excelPart = true
        if (name.startsWith("ppt/")) powerPointPart = true
    }

    /** Executable signs win over document signs: a "document" that carries code is code. */
    fun classify(): DetectedType = when {
        androidManifest -> DetectedType.APK
        bundleManifest -> DetectedType.AAB
        apkSetToc && nestedApk -> DetectedType.APKS
        nestedApk -> DetectedType.XAPK
        dex -> DetectedType.DEX
        appxManifest -> DetectedType.APPX
        iosPayload -> DetectedType.IPA
        classFile || jarManifest -> DetectedType.JAR
        vbaProject || odfMacros -> DetectedType.OFFICE_MACRO
        ooxmlContentTypes && wordPart -> DetectedType.DOCX
        ooxmlContentTypes && excelPart -> DetectedType.XLSX
        ooxmlContentTypes && powerPointPart -> DetectedType.PPTX
        mimetype && epubContainer -> DetectedType.EPUB
        mimetype && odfManifest -> DetectedType.ODF
        else -> DetectedType.ZIP
    }

    private companion object {
        val NESTED_APP_EXTENSIONS = listOf(".apk", ".apks", ".xapk", ".apkm")
    }
}

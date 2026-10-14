package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType

/**
 * Decides what a file really is from its bytes (golden rule 5). File names and server
 * MIME types are never consulted here; see [TypePolicy] and [ClaimedType] for those.
 *
 * Rules run strongest first: executables, then containers that need a deeper look,
 * then media and archives, and plain text last. Anything unrecognised is UNKNOWN,
 * which the policy treats as blocked.
 */
object MagicSniffer {
    /** Enough for every signature here and a fair text sample. */
    const val HEAD_SIZE = 8 * 1024

    fun sniff(bytes: ByteArray): DetectedType = sniff(ByteArraySource(bytes))

    fun sniff(source: ByteSource): DetectedType {
        if (source.size == 0L) return DetectedType.EMPTY
        val probe = Probe(source, source.readHead(HEAD_SIZE))
        val type = RULES.firstNotNullOfOrNull { rule -> rule(probe) } ?: DetectedType.UNKNOWN
        return if (type in NO_TRAILER_CHECK) type else hiddenApp(source) ?: type
    }

    /**
     * A file can look like a photo at the front and still be an installable app: ZIP
     * readers find the archive from the END of the file. Catch app packages stuck on
     * the back of anything else.
     */
    private fun hiddenApp(source: ByteSource): DetectedType? =
        ZipInspector.inspect(source)?.takeIf { it in ZipInspector.EXECUTABLE_TYPES }

    private val RULES: List<(Probe) -> DetectedType?> = listOf(
        ::executable,
        ::zipBased,
        ::document,
        ::image,
        ::video,
        ::audio,
        ::compressed,
        ::text,
    )

    // Types that are already blocked, or ZIPs whose directory was just read. UNKNOWN is
    // still checked so a hidden app gets the more useful label.
    private val NO_TRAILER_CHECK: Set<DetectedType> = buildSet {
        addAll(ZipInspector.EXECUTABLE_TYPES)
        addAll(
            listOf(
                DetectedType.ZIP, DetectedType.DOCX, DetectedType.XLSX, DetectedType.PPTX,
                DetectedType.ODF, DetectedType.EPUB, DetectedType.OFFICE_MACRO,
                DetectedType.PE_EXE, DetectedType.ELF, DetectedType.MACH_O, DetectedType.JAVA_CLASS,
                DetectedType.MSI, DetectedType.LNK, DetectedType.CRX, DetectedType.LINUX_PACKAGE,
                DetectedType.SCRIPT,
            ),
        )
    }

    private val MACH_O_MAGICS = listOf(
        bytesOf(0xFE, 0xED, 0xFA, 0xCE),
        bytesOf(0xFE, 0xED, 0xFA, 0xCF),
        bytesOf(0xCE, 0xFA, 0xED, 0xFE),
        bytesOf(0xCF, 0xFA, 0xED, 0xFE),
    )
    private val CAFEBABE = bytesOf(0xCA, 0xFE, 0xBA, 0xBE)
    private val ELF_MAGIC = bytesOf(0x7F, 0x45, 0x4C, 0x46)
    private val OLE_MAGIC = bytesOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
    private val LNK_MAGIC = bytesOf(0x4C, 0x00, 0x00, 0x00, 0x01, 0x14, 0x02, 0x00)
    private val RPM_MAGIC = bytesOf(0xED, 0xAB, 0xEE, 0xDB)
    private val ZIP_MAGICS = listOf(
        bytesOf(0x50, 0x4B, 0x03, 0x04),
        bytesOf(0x50, 0x4B, 0x05, 0x06),
        bytesOf(0x50, 0x4B, 0x07, 0x08),
    )
    private val PNG_MAGIC = bytesOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG_MAGIC = bytesOf(0xFF, 0xD8, 0xFF)
    private val EBML_MAGIC = bytesOf(0x1A, 0x45, 0xDF, 0xA3)
    private val GZIP_MAGIC = bytesOf(0x1F, 0x8B, 0x08)
    private val XZ_MAGIC = bytesOf(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00)
    private val SEVEN_ZIP_MAGIC = bytesOf(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)
    private val RAR_MAGIC = bytesOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)
    private val ZSTD_MAGIC = bytesOf(0x28, 0xB5, 0x2F, 0xFD)
    private val CAB_MAGIC = bytesOf(0x4D, 0x53, 0x43, 0x46, 0x00, 0x00, 0x00, 0x00)

    /** Java class files and fat Mach-O binaries share CAFEBABE; both are blocked. */
    private const val FIRST_JAVA_CLASS_MAJOR_VERSION = 45
    private const val BMP_MIN_SIZE = 18
    private val BMP_DIB_HEADER_SIZES = setOf(12L, 40L, 52L, 56L, 64L, 108L, 124L)
    private const val TS_PACKET = 188
    private const val TS_SYNC = 0x47
    private const val TAR_MAGIC_OFFSET = 257

    private fun executable(p: Probe): DetectedType? = with(p.head) {
        when {
            hasAsciiAt(0, "MZ") -> DetectedType.PE_EXE

            hasBytesAt(0, ELF_MAGIC) -> DetectedType.ELF

            MACH_O_MAGICS.any { hasBytesAt(0, it) } -> DetectedType.MACH_O

            hasBytesAt(0, CAFEBABE) && size >= 8 ->
                if (u16be(4) == 0 && u16be(6) >= FIRST_JAVA_CLASS_MAJOR_VERSION) {
                    DetectedType.JAVA_CLASS
                } else {
                    DetectedType.MACH_O
                }

            isDex() -> DetectedType.DEX

            hasBytesAt(0, OLE_MAGIC) -> OleInspector.classify(p.source)

            hasBytesAt(0, LNK_MAGIC) -> DetectedType.LNK

            hasAsciiAt(0, "Cr24") -> DetectedType.CRX

            hasBytesAt(0, RPM_MAGIC) -> DetectedType.LINUX_PACKAGE

            hasAsciiAt(0, "!<arch>\ndebian-binary") -> DetectedType.LINUX_PACKAGE

            // AMR audio also starts with "#!", so it must be checked before scripts.
            hasAsciiAt(0, "#!AMR") -> DetectedType.AMR

            hasAsciiAt(0, "#!") -> DetectedType.SCRIPT

            else -> null
        }
    }

    private fun ByteArray.isDex(): Boolean =
        hasAsciiAt(0, "dex\n") && size >= 8 && (4..6).all { this[it].toInt().toChar().isDigit() } &&
            this[7] == 0.toByte()

    private fun zipBased(p: Probe): DetectedType? = if (ZIP_MAGICS.any { p.head.hasBytesAt(0, it) }) {
        // A ZIP whose directory cannot be read is not given the benefit of the doubt.
        ZipInspector.inspect(p.source) ?: DetectedType.UNKNOWN
    } else {
        null
    }

    private fun document(p: Probe): DetectedType? = if (p.head.hasAsciiAt(0, "%PDF-")) DetectedType.PDF else null

    private fun image(p: Probe): DetectedType? = with(p.head) {
        when {
            hasBytesAt(0, JPEG_MAGIC) -> DetectedType.JPEG
            hasBytesAt(0, PNG_MAGIC) -> DetectedType.PNG
            hasAsciiAt(0, "GIF87a") || hasAsciiAt(0, "GIF89a") -> DetectedType.GIF
            isRiff("WEBP") -> DetectedType.WEBP
            hasAsciiAt(0, "BM") && size >= BMP_MIN_SIZE && u32le(14) in BMP_DIB_HEADER_SIZES -> DetectedType.BMP
            isIco() -> DetectedType.ICO
            else -> isoBaseMedia(this)
        }
    }

    private fun ByteArray.isRiff(form: String): Boolean = hasAsciiAt(0, "RIFF") && hasAsciiAt(8, form)

    private fun ByteArray.isIco(): Boolean {
        if (size < 22 || u16le(0) != 0 || u16le(2) != 1) return false
        val count = u16le(4)
        return count in 1..255 && u8(9) == 0 && u32le(18) >= 6L + 16L * count
    }

    /** MP4, MOV, 3GP, M4A, HEIC and AVIF are all ISO base media files, told apart by brand. */
    private fun isoBaseMedia(head: ByteArray): DetectedType? {
        if (LEGACY_QUICKTIME_ATOMS.any { head.hasAsciiAt(4, it) }) return DetectedType.MOV
        if (head.size < 12 || !head.hasAsciiAt(4, "ftyp")) return null
        val major = head.asciiAt(8, 4)
        val boxEnd = minOf(head.u32be(0), head.size.toLong()).toInt()
        val brands = (16 until boxEnd - 3 step 4).map { head.asciiAt(it, 4) } + major
        return when {
            major == "avif" || major == "avis" -> DetectedType.AVIF
            major in HEIF_BRANDS && ("avif" in brands || "avis" in brands) -> DetectedType.AVIF
            major in HEIF_BRANDS || major in HEIC_BRANDS -> DetectedType.HEIC
            major in AUDIO_BRANDS -> DetectedType.M4A
            major == "qt  " -> DetectedType.MOV
            major.startsWith("3g") -> DetectedType.THREE_GP
            brands.any(::isMp4Brand) -> DetectedType.MP4
            else -> null
        }
    }

    private val LEGACY_QUICKTIME_ATOMS = listOf("moov", "mdat", "wide", "pnot")
    private val HEIF_BRANDS = setOf("mif1", "msf1")
    private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs")
    private val AUDIO_BRANDS = setOf("M4A ", "M4B ", "M4P ")
    private val MP4_BRAND_PREFIXES = listOf("iso", "mp4", "mp7", "avc", "M4V", "dash", "msdh", "cmf", "f4v", "mmp4")

    private fun isMp4Brand(brand: String) = MP4_BRAND_PREFIXES.any(brand::startsWith)

    private fun video(p: Probe): DetectedType? = with(p.head) {
        when {
            hasBytesAt(0, EBML_MAGIC) -> if (asciiAt(0, minOf(size, 64)).contains("webm")) {
                DetectedType.WEBM
            } else {
                DetectedType.MKV
            }

            isRiff("AVI ") -> DetectedType.AVI

            hasAsciiAt(0, "FLV") && size > 3 && this[3] == 1.toByte() -> DetectedType.FLV

            size > 2 * TS_PACKET && (0..2).all { u8(it * TS_PACKET) == TS_SYNC } -> DetectedType.MPEG_TS

            else -> null
        }
    }

    private fun audio(p: Probe): DetectedType? = with(p.head) {
        when {
            isRiff("WAVE") -> DetectedType.WAV
            hasAsciiAt(0, "OggS") -> DetectedType.OGG
            hasAsciiAt(0, "fLaC") -> DetectedType.FLAC
            hasAsciiAt(0, "ID3") && size >= 10 -> afterId3Tag(p)
            else -> null
        }
    }

    /** ID3 tags front MP3s mostly, but FLAC and AAC files carry them too. */
    private fun afterId3Tag(p: Probe): DetectedType {
        val head = p.head
        val tagSize = (head.u8(6) shl 21) or (head.u8(7) shl 14) or (head.u8(8) shl 7) or head.u8(9)
        val footer = if (head.u8(5) and 0x10 != 0) 10 else 0
        val after = p.source.readFully(10L + tagSize + footer, 4) ?: return DetectedType.MP3
        return when {
            after.hasAsciiAt(0, "fLaC") -> DetectedType.FLAC
            isAdtsFrame(after) -> DetectedType.AAC
            else -> DetectedType.MP3
        }
    }

    private fun compressed(p: Probe): DetectedType? = with(p.head) {
        when {
            hasBytesAt(0, GZIP_MAGIC) -> DetectedType.GZIP
            hasAsciiAt(0, "BZh") && size >= 4 && this[3].toInt().toChar() in '1'..'9' -> DetectedType.BZIP2
            hasBytesAt(0, XZ_MAGIC) -> DetectedType.XZ
            hasBytesAt(0, SEVEN_ZIP_MAGIC) -> DetectedType.SEVEN_ZIP
            hasBytesAt(0, RAR_MAGIC) -> DetectedType.RAR
            hasBytesAt(0, ZSTD_MAGIC) -> DetectedType.ZSTD
            hasBytesAt(0, CAB_MAGIC) -> DetectedType.CAB
            hasAsciiAt(TAR_MAGIC_OFFSET, "ustar") -> DetectedType.TAR
            else -> null
        }
    }

    /**
     * Byte order marks first: a UTF-16LE mark (FF FE) would otherwise pass for an MPEG
     * audio frame header. Raw MPEG and ADTS frames have the weakest signatures, so they
     * come after everything else but before the generic text test.
     */
    private fun text(p: Probe): DetectedType? = with(p.head) {
        when {
            TextHeuristics.hasUnicodeBom(this) -> DetectedType.TEXT
            isAdtsFrame(this) -> DetectedType.AAC
            isMpegAudioFrame(this) -> DetectedType.MP3
            TextHeuristics.looksLikeText(this) -> DetectedType.TEXT
            else -> null
        }
    }

    private fun isAdtsFrame(b: ByteArray): Boolean =
        b.size >= 4 && b.u8(0) == 0xFF && (b.u8(1) and 0xF6) == 0xF0 && ((b.u8(2) shr 2) and 0x0F) < 13

    private fun isMpegAudioFrame(b: ByteArray): Boolean {
        if (b.size < 4 || b.u8(0) != 0xFF || (b.u8(1) and 0xE0) != 0xE0) return false
        val version = (b.u8(1) shr 3) and 0x03
        val layer = (b.u8(1) shr 1) and 0x03
        val bitrate = (b.u8(2) shr 4) and 0x0F
        val sampleRate = (b.u8(2) shr 2) and 0x03
        return version != 1 && layer != 0 && bitrate != 0x0F && sampleRate != 0x03
    }

    private class Probe(val source: ByteSource, val head: ByteArray)
}

package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.filetype.Fixtures.ascii
import dev.ikram.sandbox.core.filetype.Fixtures.bytes
import dev.ikram.sandbox.core.filetype.Fixtures.padded
import dev.ikram.sandbox.core.model.DetectedType
import org.junit.Assert.assertEquals
import org.junit.Test

class MagicSnifferTest {
    private fun assertSniffs(expected: DetectedType, bytes: ByteArray) {
        assertEquals(expected, MagicSniffer.sniff(bytes))
    }

    // Images

    @Test fun jpeg() = assertSniffs(DetectedType.JPEG, Fixtures.jpeg())

    @Test fun png() = assertSniffs(DetectedType.PNG, Fixtures.png())

    @Test fun gif() = assertSniffs(DetectedType.GIF, Fixtures.gif())

    @Test fun gif87a() = assertSniffs(DetectedType.GIF, padded(ascii("GIF87a"), 64))

    @Test fun webp() = assertSniffs(DetectedType.WEBP, Fixtures.webp())

    @Test fun bmp() = assertSniffs(DetectedType.BMP, Fixtures.bmp())

    @Test fun ico() = assertSniffs(DetectedType.ICO, Fixtures.ico())

    @Test fun heic() = assertSniffs(DetectedType.HEIC, Fixtures.isoBmff("heic", "mif1", "heic"))

    @Test fun heifWithHevcBrand() = assertSniffs(DetectedType.HEIC, Fixtures.isoBmff("mif1", "mif1", "heic"))

    @Test fun avif() = assertSniffs(DetectedType.AVIF, Fixtures.isoBmff("avif", "avif", "mif1"))

    @Test fun avifUnderGenericHeifBrand() = assertSniffs(DetectedType.AVIF, Fixtures.isoBmff("mif1", "mif1", "avif"))

    @Test fun textStartingWithBmIsNotABitmap() = assertSniffs(DetectedType.TEXT, ascii("BMW owners club newsletter\n"))

    // Video

    @Test fun mp4() = assertSniffs(DetectedType.MP4, Fixtures.isoBmff("isom", "isom", "iso2", "avc1", "mp41"))

    @Test fun mp4Brand42() = assertSniffs(DetectedType.MP4, Fixtures.isoBmff("mp42", "mp42", "isom"))

    @Test fun mp4UnknownMajorWithCompatibleBrand() = assertSniffs(DetectedType.MP4, Fixtures.isoBmff("XYZ1", "isom"))

    @Test fun mov() = assertSniffs(DetectedType.MOV, Fixtures.isoBmff("qt  ", "qt  "))

    @Test fun legacyMovWithoutFtyp() = assertSniffs(DetectedType.MOV, Fixtures.be32(8) + ascii("moov") + ByteArray(64))

    @Test fun threeGp() = assertSniffs(DetectedType.THREE_GP, Fixtures.isoBmff("3gp5", "3gp5", "isom"))

    @Test fun webm() = assertSniffs(DetectedType.WEBM, Fixtures.ebml("webm"))

    @Test fun mkv() = assertSniffs(DetectedType.MKV, Fixtures.ebml("matroska"))

    @Test fun avi() = assertSniffs(DetectedType.AVI, Fixtures.avi())

    @Test fun flv() = assertSniffs(DetectedType.FLV, Fixtures.flv())

    @Test fun mpegTransportStream() = assertSniffs(DetectedType.MPEG_TS, Fixtures.mpegTs())

    @Test fun unknownIsoBrandIsUnknown() = assertSniffs(DetectedType.UNKNOWN, Fixtures.isoBmff("crx ", "crx "))

    // Audio

    @Test fun m4a() = assertSniffs(DetectedType.M4A, Fixtures.isoBmff("M4A ", "M4A ", "mp42", "isom"))

    @Test fun mp3WithId3() = assertSniffs(DetectedType.MP3, Fixtures.id3(Fixtures.mp3Frame()))

    @Test fun mp3RawFrame() = assertSniffs(DetectedType.MP3, Fixtures.mp3Frame())

    @Test fun aacAdts() = assertSniffs(DetectedType.AAC, Fixtures.adtsFrame())

    @Test fun aacBehindId3() = assertSniffs(DetectedType.AAC, Fixtures.id3(Fixtures.adtsFrame()))

    @Test fun flacBehindId3() = assertSniffs(DetectedType.FLAC, Fixtures.id3(Fixtures.flac(), tagSize = 20_000))

    @Test fun ogg() = assertSniffs(DetectedType.OGG, Fixtures.ogg())

    @Test fun flac() = assertSniffs(DetectedType.FLAC, Fixtures.flac())

    @Test fun wav() = assertSniffs(DetectedType.WAV, Fixtures.wav())

    @Test fun amrIsAudioNotAScript() = assertSniffs(DetectedType.AMR, Fixtures.amr())

    // Documents, text and archives

    @Test fun pdf() = assertSniffs(DetectedType.PDF, Fixtures.pdf())

    @Test fun gzip() = assertSniffs(DetectedType.GZIP, Fixtures.gzip())

    @Test fun bzip2() = assertSniffs(DetectedType.BZIP2, Fixtures.bzip2())

    @Test fun xz() = assertSniffs(DetectedType.XZ, Fixtures.xz())

    @Test fun sevenZip() = assertSniffs(DetectedType.SEVEN_ZIP, Fixtures.sevenZip())

    @Test fun rar() = assertSniffs(DetectedType.RAR, Fixtures.rar())

    @Test fun zstd() = assertSniffs(DetectedType.ZSTD, Fixtures.zstd())

    @Test fun cab() = assertSniffs(DetectedType.CAB, Fixtures.cab())

    @Test fun tar() = assertSniffs(DetectedType.TAR, Fixtures.tar())

    @Test fun oldOfficeDocument() = assertSniffs(DetectedType.OLE_DOCUMENT, Fixtures.ole(Fixtures.WORD_CLSID))

    // Executables

    @Test fun windowsExe() = assertSniffs(DetectedType.PE_EXE, Fixtures.peExe())

    @Test fun bareMzHeaderIsStillBlocked() = assertSniffs(DetectedType.PE_EXE, padded(ascii("MZ"), 64))

    @Test fun elf() = assertSniffs(DetectedType.ELF, Fixtures.elf())

    @Test fun machO() = assertSniffs(DetectedType.MACH_O, Fixtures.machO())

    @Test fun fatMachO() = assertSniffs(DetectedType.MACH_O, Fixtures.fatMachO())

    @Test fun javaClass() = assertSniffs(DetectedType.JAVA_CLASS, Fixtures.javaClass())

    @Test fun dex() = assertSniffs(DetectedType.DEX, Fixtures.dex())

    @Test fun msi() = assertSniffs(DetectedType.MSI, Fixtures.ole(Fixtures.MSI_CLSID))

    @Test fun msiPatch() {
        val clsid = Fixtures.MSI_CLSID.copyOf().also { it[0] = 0x86.toByte() }
        assertSniffs(DetectedType.MSI, Fixtures.ole(clsid))
    }

    @Test fun truncatedOleIsTreatedAsInstaller() =
        assertSniffs(DetectedType.MSI, bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1))

    @Test fun windowsShortcut() = assertSniffs(DetectedType.LNK, Fixtures.lnk())

    @Test fun chromeExtension() = assertSniffs(DetectedType.CRX, Fixtures.crx())

    @Test fun rpm() = assertSniffs(DetectedType.LINUX_PACKAGE, Fixtures.rpm())

    @Test fun deb() = assertSniffs(DetectedType.LINUX_PACKAGE, Fixtures.deb())

    @Test fun shebangScript() = assertSniffs(DetectedType.SCRIPT, ascii("#!/bin/sh\nrm -rf ~/\n"))

    @Test fun shebangWithBlankLinesAfterIsStillAScript() =
        assertSniffs(DetectedType.SCRIPT, ascii("#!/usr/bin/env python3\n\n"))

    // Edge cases

    @Test fun emptyFile() = assertSniffs(DetectedType.EMPTY, ByteArray(0))

    @Test fun singleByteText() = assertSniffs(DetectedType.TEXT, ascii("a"))

    @Test fun singleNulByteIsUnknown() = assertSniffs(DetectedType.UNKNOWN, bytes(0))

    @Test fun signatureOnlyFilesDoNotCrash() {
        listOf(
            bytes(0xFF),
            bytes(0xFF, 0xD8),
            ascii("PK"),
            ascii("RIFF"),
            ascii("ID3"),
            ascii("BZh"),
            bytes(0x1A, 0x45),
        )
            .forEach { MagicSniffer.sniff(it) }
    }
}

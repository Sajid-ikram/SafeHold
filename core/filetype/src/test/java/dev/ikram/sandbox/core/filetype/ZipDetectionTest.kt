package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.filetype.Fixtures.zip
import dev.ikram.sandbox.core.model.DetectedType
import org.junit.Assert.assertEquals
import org.junit.Test

class ZipDetectionTest {
    private fun assertSniffs(expected: DetectedType, bytes: ByteArray) {
        assertEquals(expected, MagicSniffer.sniff(bytes))
    }

    // Android

    @Test fun apk() = assertSniffs(DetectedType.APK, Fixtures.apk())

    @Test fun apkWithOnlyAManifest() = assertSniffs(DetectedType.APK, zip("AndroidManifest.xml"))

    @Test fun apkManifestNameCaseDoesNotMatter() = assertSniffs(DetectedType.APK, zip("ANDROIDMANIFEST.XML"))

    @Test fun apkManifestWithLeadingDotSlash() = assertSniffs(DetectedType.APK, zip("./AndroidManifest.xml"))

    @Test fun apkManifestWithBackslashes() = assertSniffs(DetectedType.APK, zip("\\AndroidManifest.xml"))

    @Test fun appBundle() = assertSniffs(
        DetectedType.AAB,
        zip("base/manifest/AndroidManifest.xml", "base/dex/classes.dex"),
    )

    @Test fun apkSet() = assertSniffs(DetectedType.APKS, zip("toc.pb", "splits/base-master.apk"))

    @Test fun xapk() = assertSniffs(DetectedType.XAPK, zip("manifest.json", "com.example.game.apk", "icon.png"))

    @Test fun apkHiddenInsideAnOrdinaryZip() = assertSniffs(DetectedType.XAPK, zip("photos/a.jpg", "extras/setup.apk"))

    @Test fun looseDex() = assertSniffs(DetectedType.DEX, zip("classes.dex"))

    // Other platforms

    @Test fun jar() = assertSniffs(DetectedType.JAR, zip("META-INF/MANIFEST.MF", "com/example/Main.class"))

    @Test fun jarWithoutManifest() = assertSniffs(DetectedType.JAR, zip("Main.class"))

    @Test fun msix() = assertSniffs(DetectedType.APPX, zip("AppxManifest.xml", "App.exe"))

    @Test fun msixBundle() = assertSniffs(DetectedType.APPX, zip("AppxMetadata/AppxBundleManifest.xml", "x64.msix"))

    @Test fun ipa() = assertSniffs(
        DetectedType.IPA,
        zip("Payload/Example.app/Info.plist", "Payload/Example.app/Example"),
    )

    // Documents

    @Test fun docx() = assertSniffs(DetectedType.DOCX, zip("[Content_Types].xml", "_rels/.rels", "word/document.xml"))

    @Test fun xlsx() = assertSniffs(DetectedType.XLSX, zip("[Content_Types].xml", "xl/workbook.xml"))

    @Test fun pptx() = assertSniffs(DetectedType.PPTX, zip("[Content_Types].xml", "ppt/presentation.xml"))

    @Test fun macroEnabledWordDocument() =
        assertSniffs(DetectedType.OFFICE_MACRO, zip("[Content_Types].xml", "word/document.xml", "word/vbaProject.bin"))

    @Test fun openDocumentText() = assertSniffs(
        DetectedType.ODF,
        zip("mimetype", "content.xml", "META-INF/manifest.xml"),
    )

    @Test fun openDocumentWithBasicMacros() = assertSniffs(
        DetectedType.OFFICE_MACRO,
        zip("mimetype", "content.xml", "META-INF/manifest.xml", "Basic/Standard/Module1.xml"),
    )

    @Test fun epub() = assertSniffs(DetectedType.EPUB, zip("mimetype", "META-INF/container.xml", "OEBPS/content.opf"))

    @Test fun documentCarryingCodeIsCode() =
        assertSniffs(DetectedType.JAR, zip("[Content_Types].xml", "word/document.xml", "word/Payload.class"))

    // Plain and unusual ZIPs

    @Test fun plainZip() = assertSniffs(DetectedType.ZIP, zip("photos/a.jpg", "photos/b.jpg", "notes.txt"))

    /** Just an end record. ZipOutputStream refuses to write this, so it is built by hand. */
    @Test fun emptyZip() = assertSniffs(DetectedType.ZIP, Fixtures.bytes(0x50, 0x4B, 0x05, 0x06) + ByteArray(18))

    @Test fun zipWithComment() = assertSniffs(DetectedType.APK, zip("AndroidManifest.xml", comment = "made by a tool"))

    @Test fun zip64() = assertSniffs(DetectedType.APK, Fixtures.toZip64(Fixtures.apk()))

    @Test fun zip64PlainZip() = assertSniffs(DetectedType.ZIP, Fixtures.toZip64(zip("a.txt", "b.txt")))

    @Test fun nonAsciiEntryNames() = assertSniffs(DetectedType.ZIP, zip("fotos/café.jpg", "日本語.txt"))

    @Test fun manyEntries() {
        val names = (0 until 5_000).map { "files/file-$it.txt" } + "AndroidManifest.xml"
        assertSniffs(DetectedType.APK, zip(*names.toTypedArray()))
    }

    @Test fun truncatedZipIsUnknown() {
        val apk = Fixtures.apk()
        assertSniffs(DetectedType.UNKNOWN, apk.copyOfRange(0, apk.size - 40))
    }

    @Test fun zipHeaderWithoutDirectoryIsUnknown() =
        assertSniffs(DetectedType.UNKNOWN, Fixtures.padded(Fixtures.bytes(0x50, 0x4B, 0x03, 0x04), 512, filler = 0x41))

    @Test fun corruptedCentralDirectoryIsUnknown() {
        val apk = Fixtures.apk()
        val directory = apk.size - 22 - 200
        val corrupted = apk.copyOf().also { it.fill(0x41, directory, directory + 50) }
        assertSniffs(DetectedType.UNKNOWN, corrupted)
    }

    // Polyglots: harmless at the front, an app at the back

    @Test fun apkAppendedToJpeg() = assertSniffs(DetectedType.APK, Fixtures.jpeg() + Fixtures.apk())

    @Test fun jarAppendedToPdf() = assertSniffs(DetectedType.JAR, Fixtures.pdf() + zip("Main.class"))

    @Test fun apkAppendedToText() = assertSniffs(DetectedType.APK, Fixtures.ascii("just some notes\n") + Fixtures.apk())

    @Test fun ordinaryZipAppendedToJpegStaysJpeg() = assertSniffs(
        DetectedType.JPEG,
        Fixtures.jpeg() + zip("photos/a.jpg"),
    )

    @Test fun selfExtractingExeStaysExe() = assertSniffs(DetectedType.PE_EXE, Fixtures.peExe() + Fixtures.apk())
}

package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.filetype.Fixtures.ascii
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** End to end: real bytes plus the name and MIME type a server claimed. */
class DisguisedFileTest {
    private fun classify(bytes: ByteArray, name: String?, mime: String? = null) =
        FileClassifier.classify(ByteArraySource(bytes), name, mime)

    private fun assertDisguised(bytes: ByteArray, name: String, detected: DetectedType, claimed: DetectedType) {
        val result = classify(bytes, name)
        assertEquals(detected, result.detectedType)
        assertEquals(claimed, result.claimedType)
        assertEquals(FileCategory.BLOCKED, result.category)
        assertTrue("$name should be flagged", result.isMismatch)
        assertFalse(result.rules.canOpen)
        assertFalse(result.rules.canShareOriginal)
    }

    @Test fun apkNamedAsVideo() = assertDisguised(
        Fixtures.apk(),
        "free-movie-hd.mp4",
        DetectedType.APK,
        DetectedType.MP4,
    )

    @Test fun exeNamedAsPdf() = assertDisguised(Fixtures.peExe(), "invoice.pdf", DetectedType.PE_EXE, DetectedType.PDF)

    @Test fun elfNamedAsMusic() = assertDisguised(Fixtures.elf(), "track01.mp3", DetectedType.ELF, DetectedType.MP3)

    @Test fun jarNamedAsPhoto() =
        assertDisguised(Fixtures.zip("Main.class"), "holiday.jpg", DetectedType.JAR, DetectedType.JPEG)

    @Test fun msiNamedAsDocument() =
        assertDisguised(Fixtures.ole(Fixtures.MSI_CLSID), "statement.doc", DetectedType.MSI, DetectedType.OLE_DOCUMENT)

    @Test fun xapkNamedAsArchive() =
        assertDisguised(Fixtures.zip("manifest.json", "game.apk"), "assets.zip", DetectedType.XAPK, DetectedType.ZIP)

    @Test fun macroDocumentNamedAsPlainDocx() = assertDisguised(
        Fixtures.zip("[Content_Types].xml", "word/document.xml", "word/vbaProject.bin"),
        "report.docx",
        DetectedType.OFFICE_MACRO,
        DetectedType.DOCX,
    )

    @Test fun scriptNamedAsText() =
        assertDisguised(ascii("#!/bin/sh\necho hi\n"), "readme.txt", DetectedType.SCRIPT, DetectedType.TEXT)

    @Test fun apkHiddenBehindAPhoto() =
        assertDisguised(Fixtures.jpeg() + Fixtures.apk(), "cat.jpg", DetectedType.APK, DetectedType.JPEG)

    @Test fun rightToLeftOverrideTrick() {
        // Displays as "photoexe.jpg"; the real extension is .exe, and the bytes agree.
        val result = classify(Fixtures.peExe(), "photo‮gpj.exe")
        assertEquals(DetectedType.PE_EXE, result.detectedType)
        assertEquals(DetectedType.PE_EXE, result.claimedType)
        assertEquals(FileCategory.BLOCKED, result.category)
    }

    @Test fun doubleExtensionUsesTheLastOne() {
        val result = classify(Fixtures.peExe(), "movie.mp4.exe")
        assertEquals(DetectedType.PE_EXE, result.claimedType)
        assertFalse(result.isMismatch)
    }

    @Test fun pngNamedAsJpegIsNotAMismatch() {
        val result = classify(Fixtures.png(), "photo.jpg")
        assertEquals(DetectedType.PNG, result.detectedType)
        assertEquals(FileCategory.IMAGE, result.category)
        assertFalse(result.isMismatch)
        assertTrue(result.rules.canOpen)
    }

    @Test fun audioOnlyMp4NamedM4aIsNotAMismatch() {
        val result = classify(Fixtures.isoBmff("mp42", "mp42", "isom"), "song.m4a")
        assertFalse(result.isMismatch)
    }

    @Test fun shellScriptWithoutShebangIsBlockedByName() {
        val result = classify(ascii("echo hello\n"), "setup-notes.sh")
        assertEquals(DetectedType.SCRIPT, result.detectedType)
        assertEquals(FileCategory.BLOCKED, result.category)
        assertFalse(result.isMismatch)
    }

    @Test fun htmlErrorPageInsteadOfPdf() {
        val result = classify(ascii("<html><body>404 Not Found</body></html>"), "report.pdf", "text/html")
        assertEquals(DetectedType.TEXT, result.detectedType)
        assertEquals(DetectedType.PDF, result.claimedType)
        assertTrue(result.isMismatch)
    }

    @Test fun mimeTypeIsUsedWhenTheNameSaysNothing() {
        val result = classify(Fixtures.peExe(), "download", "application/pdf")
        assertEquals(DetectedType.PDF, result.claimedType)
        assertTrue(result.isMismatch)
    }

    @Test fun genericMimeAndNoExtensionClaimNothing() {
        val result = classify(Fixtures.pdf(), "download", "application/octet-stream")
        assertNull(result.claimedType)
        assertFalse(result.isMismatch)
        assertEquals(DetectedType.PDF, result.detectedType)
    }

    @Test fun honestFilesPassThrough() {
        val honest = mapOf(
            "map.pdf" to Fixtures.pdf(),
            "sunrise.jpg" to Fixtures.jpeg(),
            "episode.mp3" to Fixtures.id3(Fixtures.mp3Frame()),
            "clip.webm" to Fixtures.ebml("webm"),
            "notes.txt" to ascii("hello\n"),
            "photos.zip" to Fixtures.zip("a.jpg"),
        )
        honest.forEach { (name, bytes) ->
            val result = classify(bytes, name)
            assertFalse("$name flagged", result.isMismatch)
            assertTrue("$name shareable", result.rules.canShareOriginal)
        }
    }
}

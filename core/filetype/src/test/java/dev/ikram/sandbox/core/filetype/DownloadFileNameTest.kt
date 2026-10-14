package dev.ikram.sandbox.core.filetype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadFileNameTest {
    @Test
    fun contentDispositionForms() {
        assertEquals("map.pdf", ContentDisposition.fileName("attachment; filename=\"map.pdf\""))
        assertEquals("map.pdf", ContentDisposition.fileName("attachment; filename=map.pdf"))
        assertEquals("map.pdf", ContentDisposition.fileName("ATTACHMENT; FILENAME = \"map.pdf\""))
        assertEquals(
            "a \"quoted\" name.txt",
            ContentDisposition.fileName("inline; filename=\"a \\\"quoted\\\" name.txt\""),
        )
        assertEquals("semi;colon.txt", ContentDisposition.fileName("attachment; filename=\"semi;colon.txt\"; size=10"))
        assertEquals("x.txt", ContentDisposition.fileName("attachment; foo; filename=x.txt"))
    }

    @Test
    fun extendedFilenameWinsAndIsDecoded() {
        val header = "attachment; filename=\"fallback.txt\"; filename*=UTF-8''na%C3%AFve%20caf%C3%A9.txt"
        assertEquals("naïve café.txt", ContentDisposition.fileName(header))
    }

    @Test
    fun extendedFilenameKeepsPlusSigns() {
        assertEquals("a+b.txt", ContentDisposition.fileName("attachment; filename*=UTF-8''a+b.txt"))
    }

    @Test
    fun badExtendedFilenameFallsBackToPlain() {
        assertEquals("plain.txt", ContentDisposition.fileName("attachment; filename*=NOPE''x.txt; filename=plain.txt"))
    }

    @Test
    fun noFilename() {
        assertNull(ContentDisposition.fileName("attachment"))
        assertNull(ContentDisposition.fileName(null))
        assertNull(ContentDisposition.fileName("attachment; filename=\"\""))
    }

    @Test
    fun nameFromHeader() = assertEquals(
        "north-coast-map.pdf",
        DownloadFileName.resolve("https://x.example/dl?id=1", "attachment; filename=\"north-coast-map.pdf\"", null),
    )

    @Test
    fun nameFromUrlIsDecoded() = assertEquals(
        "trail map.pdf",
        DownloadFileName.resolve("https://x.example/files/trail%20map.pdf?v=2#top", null, null),
    )

    @Test
    fun headerPathsAreStripped() = assertEquals(
        "passwd",
        DownloadFileName.resolve("https://x.example/", "attachment; filename=\"../../etc/passwd\"", null),
    )

    @Test
    fun extensionAddedFromMimeWhenMissing() =
        assertEquals("report.pdf", DownloadFileName.resolve("https://x.example/report", null, "application/pdf"))

    @Test
    fun existingExtensionIsKept() =
        assertEquals("song.mp3", DownloadFileName.resolve("https://x.example/song.mp3", null, "application/pdf"))

    @Test
    fun genericMimeAddsNothing() =
        assertEquals("blob", DownloadFileName.resolve("https://x.example/blob", null, "application/octet-stream"))

    @Test
    fun scriptMimeDoesNotPretendToBeText() =
        assertEquals("install", DownloadFileName.resolve("https://x.example/install", null, "application/x-sh"))

    @Test
    fun noUsableNameFallsBack() =
        assertEquals(NameSanitizer.FALLBACK_NAME, DownloadFileName.resolve("https://x.example/", null, null))
}

package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaimedTypeTest {
    @Test
    fun fromFileName() {
        assertEquals(DetectedType.JPEG, ClaimedType.fromFileName("photo.JPEG"))
        assertEquals(DetectedType.APK, ClaimedType.fromFileName("game.apk"))
        assertEquals(DetectedType.SCRIPT, ClaimedType.fromFileName("install.sh"))
        assertEquals(DetectedType.TEXT, ClaimedType.fromFileName("notes.md"))
        assertNull(ClaimedType.fromFileName("component.ts"))
        assertNull(ClaimedType.fromFileName("no-extension"))
    }

    @Test
    fun fromMimeType() {
        assertEquals(DetectedType.PDF, ClaimedType.fromMimeType("application/pdf"))
        assertEquals(DetectedType.TEXT, ClaimedType.fromMimeType("text/html; charset=UTF-8"))
        assertEquals(DetectedType.MP4, ClaimedType.fromMimeType(" Video/MP4 "))
        assertEquals(DetectedType.APK, ClaimedType.fromMimeType("application/vnd.android.package-archive"))
        assertNull(ClaimedType.fromMimeType("application/octet-stream"))
        assertNull(ClaimedType.fromMimeType(null))
    }

    @Test
    fun nameWinsOverMime() {
        assertEquals(DetectedType.APK, ClaimedType.of("app.apk", "application/octet-stream"))
        assertEquals(DetectedType.MP4, ClaimedType.of("clip.mp4", "application/pdf"))
        assertEquals(DetectedType.PDF, ClaimedType.of("download", "application/pdf"))
    }
}

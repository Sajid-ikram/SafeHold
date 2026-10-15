package dev.ikram.sandbox.feature.share

import dev.ikram.sandbox.core.model.DetectedType
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareNamesTest {
    private fun name(displayName: String, type: DetectedType) = ShareNames.forShare(displayName, type)

    @Test
    fun keepsTheNameWhenItAlreadySaysWhatTheFileIs() {
        assertEquals("harbour-sunrise.jpg", name("harbour-sunrise.jpg", DetectedType.JPEG))
        assertEquals("photo.JPEG", name("photo.JPEG", DetectedType.JPEG))
        assertEquals("budget.xls", name("budget.xls", DetectedType.OLE_DOCUMENT))
        assertEquals("notes.csv", name("notes.csv", DetectedType.TEXT))
    }

    @Test
    fun aCopyInAnotherFormatSaysSo() {
        assertEquals("logo.png", name("logo.gif", DetectedType.PNG))
        assertEquals("lecture-clip.mp4", name("lecture-clip.webm", DetectedType.MP4))
        assertEquals("interview.m4a", name("interview.mp3", DetectedType.M4A))
        assertEquals("holiday.jpg", name("holiday.heic", DetectedType.JPEG))
    }

    @Test
    fun aDisguiseIsNeverPassedOn() {
        assertEquals("movie.txt", name("movie.mp4", DetectedType.TEXT))
        assertEquals("invoice.zip", name("invoice.pdf", DetectedType.ZIP))
    }

    @Test
    fun textThatABrowserWouldRunIsRenamed() {
        assertEquals("page.txt", name("page.html", DetectedType.TEXT))
        assertEquals("feed.txt", name("feed.xml", DetectedType.TEXT))
    }

    @Test
    fun namesWithoutAnExtensionGetOne() {
        assertEquals("drip.txt", name("drip", DetectedType.TEXT))
        assertEquals("download.pdf", name("", DetectedType.PDF))
    }

    @Test
    fun pathsAndHiddenCharactersNeverSurvive() {
        assertEquals("evil.jpg", name("../../evil.jpg", DetectedType.JPEG))
        assertEquals("photogpj.zip", name("photo\u202Egpj.exe", DetectedType.ZIP))
    }
}

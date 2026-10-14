package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypePolicyTest {
    /** Golden rule 4, spelled out type by type. */
    private val executables = listOf(
        DetectedType.APK, DetectedType.APKS, DetectedType.XAPK, DetectedType.AAB, DetectedType.DEX,
        DetectedType.JAR, DetectedType.JAVA_CLASS, DetectedType.PE_EXE, DetectedType.MSI, DetectedType.APPX,
        DetectedType.LNK, DetectedType.ELF, DetectedType.MACH_O, DetectedType.IPA, DetectedType.LINUX_PACKAGE,
        DetectedType.CRX, DetectedType.SCRIPT, DetectedType.OFFICE_MACRO,
    )

    @Test
    fun everyExecutableIsBlocked() {
        executables.forEach { assertEquals(it.name, FileCategory.BLOCKED, TypePolicy.categoryOf(it)) }
    }

    @Test
    fun blockedFilesCanNeverBeOpenedOrShared() {
        (executables + DetectedType.UNKNOWN + DetectedType.EMPTY).forEach { type ->
            val rules = TypePolicy.rulesFor(type)
            assertFalse("$type opens", rules.canOpen)
            assertFalse("$type shares original", rules.canShareOriginal)
            assertFalse("$type safe copies", rules.canSafeCopy)
        }
    }

    @Test
    fun onlyBlockedAndUnknownTypesAreKeptIn() {
        DetectedType.entries.forEach { type ->
            val category = TypePolicy.categoryOf(type)
            val keptIn = category == FileCategory.BLOCKED || category == FileCategory.UNKNOWN
            assertEquals(type.name, !keptIn, TypePolicy.rulesFor(type).canShareOriginal)
        }
    }

    @Test
    fun viewableCategoriesOpenAndSafeCopy() {
        listOf(FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO, FileCategory.PDF, FileCategory.TEXT)
            .forEach { category ->
                val rules = TypePolicy.rulesFor(category)
                assertTrue(rules.canOpen)
                assertTrue(rules.canSafeCopy)
                assertTrue(rules.canShareOriginal)
            }
    }

    @Test
    fun archivesShowDetailsOnly() {
        val rules = TypePolicy.rulesFor(FileCategory.ARCHIVE)
        assertFalse(rules.canOpen)
        assertFalse(rules.canSafeCopy)
        assertTrue(rules.canShareOriginal)
    }

    @Test
    fun textWithScriptExtensionBecomesScript() {
        listOf("setup.sh", "run.BAT", "invoice.js", "tool.ps1", "macro.vbs", "x.py", "notes.txt.cmd").forEach {
            assertEquals(it, DetectedType.SCRIPT, TypePolicy.restrictByName(DetectedType.TEXT, it))
        }
    }

    @Test
    fun ordinaryTextStaysText() {
        listOf("notes.txt", "data.json", "page.html", "README", null).forEach {
            assertEquals(DetectedType.TEXT, TypePolicy.restrictByName(DetectedType.TEXT, it))
        }
    }

    @Test
    fun namesNeverLoosenARestriction() {
        assertEquals(DetectedType.PE_EXE, TypePolicy.restrictByName(DetectedType.PE_EXE, "photo.jpg"))
        assertEquals(DetectedType.APK, TypePolicy.restrictByName(DetectedType.APK, "readme.txt"))
    }

    @Test
    fun nameRestrictionsOnlyApplyToText() {
        assertEquals(DetectedType.PNG, TypePolicy.restrictByName(DetectedType.PNG, "image.sh"))
    }

    @Test
    fun mismatchRules() {
        // Same category, different format: common and harmless.
        assertFalse(TypePolicy.isMismatch(DetectedType.PNG, DetectedType.JPEG))
        assertFalse(TypePolicy.isMismatch(DetectedType.ZIP, DetectedType.DOCX))
        // Audio-only MP4s are everywhere.
        assertFalse(TypePolicy.isMismatch(DetectedType.MP4, DetectedType.M4A))
        // Neither side can be opened, so there is nothing to warn about.
        assertFalse(TypePolicy.isMismatch(DetectedType.UNKNOWN, DetectedType.PE_EXE))
        // Nothing claimed, nothing to compare.
        assertFalse(TypePolicy.isMismatch(DetectedType.APK, null))
        assertFalse(TypePolicy.isMismatch(DetectedType.EMPTY, DetectedType.PDF))

        assertTrue(TypePolicy.isMismatch(DetectedType.APK, DetectedType.MP4))
        assertTrue(TypePolicy.isMismatch(DetectedType.PE_EXE, DetectedType.PDF))
        assertTrue(TypePolicy.isMismatch(DetectedType.OFFICE_MACRO, DetectedType.DOCX))
        assertTrue(TypePolicy.isMismatch(DetectedType.TEXT, DetectedType.PDF))
    }
}

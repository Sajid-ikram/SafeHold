package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.FileCategory

/** What the app may do with a file of a given category. */
data class FileRules(
    val category: FileCategory,
    /** Shown in a built in viewer. */
    val canOpen: Boolean,
    /** Shared as the downloaded bytes, behind a warning. */
    val canShareOriginal: Boolean,
    /** Shared as a copy rebuilt from scratch. */
    val canSafeCopy: Boolean,
)

object TypePolicy {
    private val OPENABLE = setOf(
        FileCategory.IMAGE,
        FileCategory.VIDEO,
        FileCategory.AUDIO,
        FileCategory.PDF,
        FileCategory.TEXT,
    )
    private val NEVER_OUT = setOf(FileCategory.BLOCKED, FileCategory.UNKNOWN)
    private val MEDIA = setOf(FileCategory.VIDEO, FileCategory.AUDIO)

    /**
     * Text files that systems run when someone taps them. The bytes are only text, so
     * these are the one case where the name matters, and it can only make a file MORE
     * restricted (golden rule 4: scripts are never opened or shared).
     */
    private val SCRIPT_EXTENSIONS = setOf(
        "bat", "cmd", "btm", "ps1", "psm1", "psd1", "ps1xml", "vbs", "vbe", "js", "jse", "mjs", "cjs",
        "wsf", "wsh", "wsc", "hta", "sct", "sh", "bash", "zsh", "ksh", "csh", "fish", "command", "tool",
        "py", "pyw", "pl", "rb", "php", "lua", "applescript", "reg", "inf", "url", "desktop", "msc",
        "jnlp", "mobileconfig", "application", "appref-ms", "settingcontent-ms", "scf", "library-ms",
        "searchconnector-ms",
    )

    /** Exhaustive on purpose: a new [DetectedType] does not compile until it has a category. */
    fun categoryOf(type: DetectedType): FileCategory = when (type) {
        DetectedType.JPEG, DetectedType.PNG, DetectedType.GIF, DetectedType.WEBP, DetectedType.BMP,
        DetectedType.HEIC, DetectedType.AVIF, DetectedType.ICO,
        -> FileCategory.IMAGE

        DetectedType.MP4, DetectedType.MOV, DetectedType.THREE_GP, DetectedType.WEBM, DetectedType.MKV,
        DetectedType.AVI, DetectedType.FLV, DetectedType.MPEG_TS,
        -> FileCategory.VIDEO

        DetectedType.MP3, DetectedType.AAC, DetectedType.M4A, DetectedType.OGG, DetectedType.FLAC,
        DetectedType.WAV, DetectedType.AMR,
        -> FileCategory.AUDIO

        DetectedType.PDF -> FileCategory.PDF

        DetectedType.TEXT -> FileCategory.TEXT

        DetectedType.ZIP, DetectedType.GZIP, DetectedType.BZIP2, DetectedType.XZ, DetectedType.ZSTD,
        DetectedType.SEVEN_ZIP, DetectedType.RAR, DetectedType.TAR, DetectedType.CAB, DetectedType.DOCX,
        DetectedType.XLSX, DetectedType.PPTX, DetectedType.ODF, DetectedType.EPUB, DetectedType.OLE_DOCUMENT,
        -> FileCategory.ARCHIVE

        DetectedType.APK, DetectedType.APKS, DetectedType.XAPK, DetectedType.AAB, DetectedType.DEX,
        DetectedType.JAR, DetectedType.JAVA_CLASS, DetectedType.PE_EXE, DetectedType.MSI, DetectedType.APPX,
        DetectedType.LNK, DetectedType.ELF, DetectedType.MACH_O, DetectedType.IPA, DetectedType.LINUX_PACKAGE,
        DetectedType.CRX, DetectedType.SCRIPT, DetectedType.OFFICE_MACRO,
        -> FileCategory.BLOCKED

        DetectedType.EMPTY, DetectedType.UNKNOWN -> FileCategory.UNKNOWN
    }

    fun rulesFor(category: FileCategory): FileRules {
        val openable = category in OPENABLE
        return FileRules(
            category = category,
            canOpen = openable,
            canShareOriginal = category !in NEVER_OUT,
            canSafeCopy = openable,
        )
    }

    fun rulesFor(type: DetectedType): FileRules = rulesFor(categoryOf(type))

    /** Turns plain text with a runnable script extension into [DetectedType.SCRIPT]. */
    fun restrictByName(detected: DetectedType, fileName: String?): DetectedType {
        val extension = NameSanitizer.extensionOf(fileName)
        return if (detected == DetectedType.TEXT && extension in SCRIPT_EXTENSIONS) DetectedType.SCRIPT else detected
    }

    fun isScriptExtension(extension: String?): Boolean = extension in SCRIPT_EXTENSIONS

    /**
     * True when the bytes and the claimed type disagree in a way worth a warning: a
     * different category. A PNG named .jpg is not a mismatch; an app named .mp4 is.
     * Video and audio count as one (an audio-only MP4 is common), and so do blocked and
     * unknown (neither can be opened, so the warning would add nothing).
     */
    fun isMismatch(detected: DetectedType, claimed: DetectedType?): Boolean {
        if (claimed == null || detected == DetectedType.EMPTY) return false
        val actual = categoryOf(detected)
        val expected = categoryOf(claimed)
        return when {
            actual == expected -> false
            actual in MEDIA && expected in MEDIA -> false
            actual in NEVER_OUT && expected in NEVER_OUT -> false
            else -> true
        }
    }
}

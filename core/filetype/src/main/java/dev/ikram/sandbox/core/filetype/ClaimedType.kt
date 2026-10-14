package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType

/**
 * What a file name or server MIME type CLAIMS a file is. Used only to spot mismatches
 * and to word the download prompt; it never decides what a file may do.
 */
object ClaimedType {
    private val BY_EXTENSION: Map<String, DetectedType> = buildMap {
        fun map(type: DetectedType, vararg extensions: String) = extensions.forEach { put(it, type) }

        map(DetectedType.JPEG, "jpg", "jpeg", "jpe", "jfif")
        map(DetectedType.PNG, "png")
        map(DetectedType.GIF, "gif")
        map(DetectedType.WEBP, "webp")
        map(DetectedType.BMP, "bmp", "dib")
        map(DetectedType.HEIC, "heic", "heif", "hif")
        map(DetectedType.AVIF, "avif")
        map(DetectedType.ICO, "ico")

        map(DetectedType.MP4, "mp4", "m4v")
        map(DetectedType.MOV, "mov", "qt")
        map(DetectedType.THREE_GP, "3gp", "3g2")
        map(DetectedType.WEBM, "webm")
        map(DetectedType.MKV, "mkv", "mka")
        map(DetectedType.AVI, "avi")
        map(DetectedType.FLV, "flv")
        // Not "ts": that is far more often TypeScript.
        map(DetectedType.MPEG_TS, "m2ts", "mts")

        map(DetectedType.MP3, "mp3")
        map(DetectedType.AAC, "aac")
        map(DetectedType.M4A, "m4a", "m4b", "m4p")
        map(DetectedType.OGG, "ogg", "oga", "opus")
        map(DetectedType.FLAC, "flac")
        map(DetectedType.WAV, "wav")
        map(DetectedType.AMR, "amr")

        map(DetectedType.PDF, "pdf")
        map(
            DetectedType.TEXT,
            "txt", "text", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "xhtml", "css",
            "log", "ini", "cfg", "conf", "yaml", "yml", "toml", "svg", "srt", "vtt", "rtf", "kt", "java",
            "c", "h", "cpp", "swift", "go", "rs", "sql", "properties", "ics", "vcf", "tex", "rst",
        )

        map(DetectedType.ZIP, "zip")
        map(DetectedType.GZIP, "gz", "tgz")
        map(DetectedType.BZIP2, "bz2", "tbz2")
        map(DetectedType.XZ, "xz", "txz")
        map(DetectedType.ZSTD, "zst")
        map(DetectedType.SEVEN_ZIP, "7z")
        map(DetectedType.RAR, "rar")
        map(DetectedType.TAR, "tar")
        map(DetectedType.CAB, "cab")
        map(DetectedType.DOCX, "docx", "dotx")
        map(DetectedType.XLSX, "xlsx", "xltx")
        map(DetectedType.PPTX, "pptx", "potx")
        map(DetectedType.ODF, "odt", "ods", "odp", "odg")
        map(DetectedType.EPUB, "epub")
        map(DetectedType.OLE_DOCUMENT, "doc", "xls", "ppt", "msg")
        map(DetectedType.OFFICE_MACRO, "docm", "dotm", "xlsm", "xltm", "xlam", "pptm", "potm", "ppam")

        map(DetectedType.APK, "apk")
        map(DetectedType.APKS, "apks")
        map(DetectedType.XAPK, "xapk", "apkm")
        map(DetectedType.AAB, "aab")
        map(DetectedType.DEX, "dex")
        map(DetectedType.JAR, "jar", "war", "ear")
        map(DetectedType.JAVA_CLASS, "class")
        map(DetectedType.PE_EXE, "exe", "dll", "scr", "cpl", "sys", "ocx", "efi", "drv")
        map(DetectedType.MSI, "msi", "msp", "mst")
        map(DetectedType.APPX, "appx", "msix", "appxbundle", "msixbundle")
        map(DetectedType.LNK, "lnk")
        map(DetectedType.ELF, "so", "elf")
        map(DetectedType.MACH_O, "dylib")
        map(DetectedType.IPA, "ipa")
        map(DetectedType.LINUX_PACKAGE, "deb", "rpm")
        map(DetectedType.CRX, "crx")
    }

    private val BY_MIME: Map<String, DetectedType> = buildMap {
        fun map(type: DetectedType, vararg mimes: String) = mimes.forEach { put(it, type) }

        map(DetectedType.JPEG, "image/jpeg", "image/pjpeg")
        map(DetectedType.PNG, "image/png")
        map(DetectedType.GIF, "image/gif")
        map(DetectedType.WEBP, "image/webp")
        map(DetectedType.BMP, "image/bmp", "image/x-ms-bmp")
        map(DetectedType.HEIC, "image/heic", "image/heif")
        map(DetectedType.AVIF, "image/avif")
        map(DetectedType.ICO, "image/x-icon", "image/vnd.microsoft.icon")

        map(DetectedType.MP4, "video/mp4")
        map(DetectedType.MOV, "video/quicktime")
        map(DetectedType.THREE_GP, "video/3gpp", "video/3gpp2")
        map(DetectedType.WEBM, "video/webm")
        map(DetectedType.MKV, "video/x-matroska")
        map(DetectedType.AVI, "video/x-msvideo", "video/avi")
        map(DetectedType.FLV, "video/x-flv")
        map(DetectedType.MPEG_TS, "video/mp2t")

        map(DetectedType.MP3, "audio/mpeg", "audio/mp3")
        map(DetectedType.AAC, "audio/aac", "audio/aacp")
        map(DetectedType.M4A, "audio/mp4", "audio/x-m4a")
        map(DetectedType.OGG, "audio/ogg", "audio/opus")
        map(DetectedType.FLAC, "audio/flac", "audio/x-flac")
        map(DetectedType.WAV, "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave")
        map(DetectedType.AMR, "audio/amr")

        map(DetectedType.PDF, "application/pdf")
        map(DetectedType.TEXT, "application/json", "application/xml", "image/svg+xml")
        map(DetectedType.SCRIPT, "application/javascript", "text/javascript", "application/x-sh", "application/x-csh")

        map(DetectedType.ZIP, "application/zip", "application/x-zip-compressed")
        map(DetectedType.GZIP, "application/gzip", "application/x-gzip")
        map(DetectedType.BZIP2, "application/x-bzip2")
        map(DetectedType.XZ, "application/x-xz")
        map(DetectedType.ZSTD, "application/zstd")
        map(DetectedType.SEVEN_ZIP, "application/x-7z-compressed")
        map(DetectedType.RAR, "application/vnd.rar", "application/x-rar-compressed")
        map(DetectedType.TAR, "application/x-tar")
        map(DetectedType.CAB, "application/vnd.ms-cab-compressed")
        map(DetectedType.DOCX, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        map(DetectedType.XLSX, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        map(DetectedType.PPTX, "application/vnd.openxmlformats-officedocument.presentationml.presentation")
        map(DetectedType.EPUB, "application/epub+zip")
        map(
            DetectedType.OLE_DOCUMENT,
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
        )

        map(DetectedType.APK, "application/vnd.android.package-archive")
        map(DetectedType.JAR, "application/java-archive", "application/x-java-archive")
        map(
            DetectedType.PE_EXE,
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/vnd.microsoft.portable-executable",
            "application/x-dosexec",
        )
        map(DetectedType.MSI, "application/x-msi", "application/x-ms-installer")
        map(DetectedType.ELF, "application/x-executable", "application/x-elf", "application/x-sharedlib")
        map(DetectedType.MACH_O, "application/x-mach-binary")
        map(DetectedType.LINUX_PACKAGE, "application/x-debian-package", "application/x-rpm")
        map(DetectedType.CRX, "application/x-chrome-extension")
    }

    fun fromFileName(fileName: String?): DetectedType? {
        val extension = NameSanitizer.extensionOf(fileName) ?: return null
        return if (TypePolicy.isScriptExtension(extension)) DetectedType.SCRIPT else BY_EXTENSION[extension]
    }

    fun fromMimeType(mimeType: String?): DetectedType? {
        val essence = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return null
        return BY_MIME[essence] ?: if (essence.startsWith("text/")) DetectedType.TEXT else null
    }

    /** The name wins: servers label far more files `application/octet-stream` than they misname. */
    fun of(fileName: String?, mimeType: String?): DetectedType? = fromFileName(fileName) ?: fromMimeType(mimeType)
}

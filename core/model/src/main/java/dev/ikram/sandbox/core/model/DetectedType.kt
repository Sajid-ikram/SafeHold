package dev.ikram.sandbox.core.model

/**
 * A file's real type, decided from its bytes by `MagicSniffer`.
 *
 * [mimeType] and [extension] describe the type itself (used for safe copies and share
 * intents). They never come from, and are never compared against, server supplied values.
 */
enum class DetectedType(val label: String, val mimeType: String, val extension: String) {
    // Images
    JPEG("JPEG image", "image/jpeg", "jpg"),
    PNG("PNG image", "image/png", "png"),
    GIF("GIF image", "image/gif", "gif"),
    WEBP("WebP image", "image/webp", "webp"),
    BMP("BMP image", "image/bmp", "bmp"),
    HEIC("HEIC image", "image/heic", "heic"),
    AVIF("AVIF image", "image/avif", "avif"),
    ICO("Icon image", "image/x-icon", "ico"),

    // Video
    MP4("MP4 video", "video/mp4", "mp4"),
    MOV("QuickTime video", "video/quicktime", "mov"),
    THREE_GP("3GP video", "video/3gpp", "3gp"),
    WEBM("WebM video", "video/webm", "webm"),
    MKV("Matroska video", "video/x-matroska", "mkv"),
    AVI("AVI video", "video/x-msvideo", "avi"),
    FLV("Flash video", "video/x-flv", "flv"),
    MPEG_TS("MPEG transport stream", "video/mp2t", "ts"),

    // Audio
    MP3("MP3 audio", "audio/mpeg", "mp3"),
    AAC("AAC audio", "audio/aac", "aac"),
    M4A("M4A audio", "audio/mp4", "m4a"),
    OGG("Ogg audio", "audio/ogg", "ogg"),
    FLAC("FLAC audio", "audio/flac", "flac"),
    WAV("WAV audio", "audio/wav", "wav"),
    AMR("AMR audio", "audio/amr", "amr"),

    // Documents
    PDF("PDF document", "application/pdf", "pdf"),
    TEXT("Plain text", "text/plain", "txt"),

    // Archives and container documents
    ZIP("ZIP archive", "application/zip", "zip"),
    GZIP("Gzip archive", "application/gzip", "gz"),
    BZIP2("Bzip2 archive", "application/x-bzip2", "bz2"),
    XZ("XZ archive", "application/x-xz", "xz"),
    ZSTD("Zstandard archive", "application/zstd", "zst"),
    SEVEN_ZIP("7-Zip archive", "application/x-7z-compressed", "7z"),
    RAR("RAR archive", "application/vnd.rar", "rar"),
    TAR("Tar archive", "application/x-tar", "tar"),
    CAB("Cabinet archive", "application/vnd.ms-cab-compressed", "cab"),
    DOCX(
        "Word document",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "docx",
    ),
    XLSX(
        "Excel spreadsheet",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xlsx",
    ),
    PPTX(
        "PowerPoint presentation",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "pptx",
    ),
    ODF("OpenDocument file", "application/vnd.oasis.opendocument.text", "odt"),
    EPUB("EPUB book", "application/epub+zip", "epub"),
    OLE_DOCUMENT("Office document (old format)", "application/x-ole-storage", "doc"),

    // Executables, installers and code
    APK("Android app (APK)", "application/vnd.android.package-archive", "apk"),
    APKS("Android app set (APKS)", "application/octet-stream", "apks"),
    XAPK("Android app bundle (XAPK)", "application/octet-stream", "xapk"),
    AAB("Android app bundle (AAB)", "application/octet-stream", "aab"),
    DEX("Android code (DEX)", "application/octet-stream", "dex"),
    JAR("Java program (JAR)", "application/java-archive", "jar"),
    JAVA_CLASS("Java class file", "application/java-vm", "class"),
    PE_EXE("Windows program (EXE)", "application/vnd.microsoft.portable-executable", "exe"),
    MSI("Windows installer (MSI)", "application/x-msi", "msi"),
    APPX("Windows app package (MSIX)", "application/octet-stream", "msix"),
    LNK("Windows shortcut", "application/x-ms-shortcut", "lnk"),
    ELF("Linux program (ELF)", "application/x-executable", "elf"),
    MACH_O("macOS program", "application/x-mach-binary", "macho"),
    IPA("iPhone app (IPA)", "application/octet-stream", "ipa"),
    LINUX_PACKAGE("Linux package", "application/octet-stream", "pkg"),
    CRX("Browser extension", "application/x-chrome-extension", "crx"),
    SCRIPT("Script", "text/plain", "txt"),
    OFFICE_MACRO("Office document with macros", "application/octet-stream", "bin"),

    // Other
    EMPTY("Empty file", "application/octet-stream", "bin"),
    UNKNOWN("Unknown file", "application/octet-stream", "bin"),
}

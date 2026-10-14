package dev.ikram.sandbox.core.model

/**
 * What the app is allowed to do with a file, grouped the way people think about files.
 * The mapping from [DetectedType] lives in `core:filetype`'s `TypePolicy`.
 */
enum class FileCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    TEXT,

    /** Archives and container documents. Kept and shown in details, but not opened in v1. */
    ARCHIVE,

    /** Executables and installers. Stored, but never opened or shared. */
    BLOCKED,

    /** Bytes we could not identify. Treated exactly like [BLOCKED]. */
    UNKNOWN,
}

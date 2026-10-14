package dev.ikram.sandbox.core.model

enum class ShareMode {
    /** Rebuilt from scratch: decoded and re-encoded, so nothing hidden survives. The default. */
    SAFE_COPY,

    /** The downloaded bytes as they are, behind a warning. Never allowed for blocked or unknown files. */
    ORIGINAL,

    /** Only the URL the file came from, as text. */
    LINK,
}

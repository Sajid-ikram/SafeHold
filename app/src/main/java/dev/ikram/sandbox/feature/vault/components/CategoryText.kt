package dev.ikram.sandbox.feature.vault.components

import androidx.annotation.StringRes
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.model.FileCategory

/** "a video", "an app or program": for sentences such as the mismatch warning. */
@StringRes
fun FileCategory.noun(): Int = when (this) {
    FileCategory.IMAGE -> R.string.category_noun_image
    FileCategory.VIDEO -> R.string.category_noun_video
    FileCategory.AUDIO -> R.string.category_noun_audio
    FileCategory.PDF -> R.string.category_noun_pdf
    FileCategory.TEXT -> R.string.category_noun_text
    FileCategory.ARCHIVE -> R.string.category_noun_archive
    FileCategory.BLOCKED -> R.string.category_noun_blocked
    FileCategory.UNKNOWN -> R.string.category_noun_unknown
}

/** "Video", "App or program": for labels. */
@StringRes
fun FileCategory.label(): Int = when (this) {
    FileCategory.IMAGE -> R.string.category_label_image
    FileCategory.VIDEO -> R.string.category_label_video
    FileCategory.AUDIO -> R.string.category_label_audio
    FileCategory.PDF -> R.string.category_label_pdf
    FileCategory.TEXT -> R.string.category_label_text
    FileCategory.ARCHIVE -> R.string.category_label_archive
    FileCategory.BLOCKED -> R.string.category_label_blocked
    FileCategory.UNKNOWN -> R.string.category_label_unknown
}

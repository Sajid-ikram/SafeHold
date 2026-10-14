package dev.ikram.sandbox.core.designsystem.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class FileTypeIconSize(val box: Dp, val corner: Dp) {
    /** Vault list rows. */
    Regular(52.dp, 12.dp),

    /** Sheets and compact rows. */
    Small(40.dp, 10.dp),
}

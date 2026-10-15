package dev.ikram.sandbox.feature.share

import android.content.Context
import androidx.core.content.FileProvider

/**
 * Serves `share_tmp/` and nothing else (res/xml/file_paths.xml, named in the manifest
 * where FileProvider.getUriForFile looks for it). Not exported: another app only ever
 * reaches a file through the read-only grant on a share intent. Its own class, so no
 * library's FileProvider can claim the same authority.
 */
class ShareFileProvider : FileProvider() {
    companion object {
        fun authority(context: Context): String = "${context.packageName}.share"
    }
}

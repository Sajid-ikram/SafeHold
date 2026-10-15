package dev.ikram.sandbox.feature.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

/**
 * Hands a prepared share to the system share sheet. Golden rule 3: always ACTION_SEND,
 * never ACTION_VIEW; files go as a FileProvider link to `share_tmp/` with a read-only
 * grant for that one link, which the system withdraws when the receiving task ends.
 */
object ShareLauncher {
    fun launch(context: Context, target: ShareTarget) {
        val send = when (target) {
            is ShareTarget.File -> fileIntent(context, target)

            is ShareTarget.Link -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, target.url)
            }
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    private fun fileIntent(context: Context, target: ShareTarget.File): Intent {
        val share = target.share
        // The name rides on the link for the receiving app to show; it is never a path.
        val uri = FileProvider.getUriForFile(
            context,
            ShareFileProvider.authority(context),
            share.file,
            share.displayName,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = share.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(share.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

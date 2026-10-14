package dev.ikram.sandbox.renderer

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.SharedMemory

/**
 * Decoded pixels, as tightly packed ARGB_8888 rows in [pixels]. The renderer makes the
 * memory read-only before sending it, and the app checks every size before trusting it.
 */
class ImageResult(
    val pixels: SharedMemory,
    val width: Int,
    val height: Int,
    /** The image's own size, before any downsampling. */
    val sourceWidth: Int,
    val sourceHeight: Int,
) : Parcelable {
    override fun describeContents(): Int = Parcelable.CONTENTS_FILE_DESCRIPTOR

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(pixels, flags)
        dest.writeInt(width)
        dest.writeInt(height)
        dest.writeInt(sourceWidth)
        dest.writeInt(sourceHeight)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<ImageResult> {
            override fun createFromParcel(source: Parcel): ImageResult = ImageResult(
                pixels = requireNotNull(readSharedMemory(source)) { "No pixels" },
                width = source.readInt(),
                height = source.readInt(),
                sourceWidth = source.readInt(),
                sourceHeight = source.readInt(),
            )

            override fun newArray(size: Int): Array<ImageResult?> = arrayOfNulls(size)
        }

        private fun readSharedMemory(source: Parcel): SharedMemory? {
            val loader = SharedMemory::class.java.classLoader
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                source.readParcelable(loader, SharedMemory::class.java)
            } else {
                @Suppress("DEPRECATION")
                source.readParcelable(loader)
            }
        }
    }
}

package dev.ikram.sandbox.renderer

import dev.ikram.sandbox.renderer.ImageDecoderImpl.Orientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationTest {
    @Test
    fun exifValuesMapInOrder() {
        // EXIF 1 to 8, as android.media.ExifInterface numbers them.
        assertEquals(
            listOf(
                Orientation.NORMAL,
                Orientation.MIRRORED,
                Orientation.ROTATED_180,
                Orientation.MIRRORED_180,
                Orientation.TRANSPOSED,
                Orientation.ROTATED_90,
                Orientation.TRANSVERSED,
                Orientation.ROTATED_270,
            ),
            (1..8).map(Orientation::fromExif),
        )
    }

    @Test
    fun missingOrNonsenseValuesAreUpright() {
        assertEquals(Orientation.NORMAL, Orientation.fromExif(0))
        assertEquals(Orientation.NORMAL, Orientation.fromExif(9))
        assertEquals(Orientation.NORMAL, Orientation.fromExif(-1))
    }

    @Test
    fun quarterTurnsSwapWidthAndHeight() {
        assertTrue(Orientation.ROTATED_90.swapsSides)
        assertTrue(Orientation.TRANSVERSED.swapsSides)
        assertFalse(Orientation.ROTATED_180.swapsSides)
        assertFalse(Orientation.MIRRORED.swapsSides)
    }
}

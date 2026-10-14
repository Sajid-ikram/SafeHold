package dev.ikram.sandbox.renderer

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real thing: a separate isolated process, fed read-only descriptors. */
@RunWith(AndroidJUnit4::class)
class SandboxRendererTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val renderer = SandboxRenderer(context)

    private fun file(name: String, bytes: ByteArray? = null, write: ((FileOutputStream) -> Unit)? = null): File =
        File(context.cacheDir, name).apply {
            FileOutputStream(this).use { out ->
                bytes?.let(out::write)
                write?.invoke(out)
            }
        }

    private fun png(width: Int, height: Int): File = file("image.png") { out ->
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(11, 107, 98))
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    @Test
    fun decodesAnImageInTheIsolatedProcess() = runBlocking {
        val bitmap = renderer.decodeImage(png(320, 200), 1024, 1024)!!
        assertEquals(320, bitmap.width)
        assertEquals(200, bitmap.height)
        assertEquals(Color.rgb(11, 107, 98), bitmap.getPixel(10, 10))
    }

    @Test
    fun downsamplesToTheRequestedSize() = runBlocking {
        val bitmap = renderer.decodeImage(png(2000, 1000), 500, 500)!!
        assertTrue(bitmap.width <= 500 && bitmap.height <= 500)
        assertEquals(2.0, bitmap.width.toDouble() / bitmap.height, 0.05)
    }

    @Test
    fun garbageIsNotAnImage() = runBlocking {
        assertNull(renderer.decodeImage(file("noise.bin", ByteArray(4096) { it.toByte() }), 512, 512))
    }

    @Test
    fun hugeDeclaredDimensionsAreRefused() = runBlocking {
        // A tiny PNG whose header claims 60000 x 60000 pixels.
        assertNull(renderer.decodeImage(file("bomb.png", pngHeader(60_000, 60_000)), 512, 512))
    }

    @Test
    fun theRendererSurvivesManyCalls() = runBlocking {
        val image = png(64, 64)
        repeat(20) { assertEquals(64, renderer.decodeImage(image, 128, 128)!!.width) }
    }

    /** PNG signature and an IHDR chunk with a valid CRC, then nothing. */
    private fun pngHeader(width: Int, height: Int): ByteArray {
        val ihdr = ByteBuffer.allocate(17).order(ByteOrder.BIG_ENDIAN)
            .put("IHDR".toByteArray()).putInt(width).putInt(height)
            .put(8).put(6).put(0).put(0).put(0)
            .array()
        val crc = CRC32().apply { update(ihdr) }.value.toInt()
        return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            ByteBuffer.allocate(4).putInt(13).array() + ihdr + ByteBuffer.allocate(4).putInt(crc).array()
    }
}

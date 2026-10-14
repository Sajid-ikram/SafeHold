package dev.ikram.sandbox.renderer

/** Hard limits, enforced inside the renderer and checked again by the app. */
object RendererLimits {
    /** Larger sources are refused outright, however small the requested output. */
    const val MAX_SOURCE_PIXELS = 50_000_000L

    /** Largest side a caller may ask for. */
    const val MAX_OUTPUT_SIDE = 4096

    const val BYTES_PER_PIXEL = 4

    /** Every call is abandoned, and the renderer killed, after this long. */
    const val CALL_TIMEOUT_MILLIS = 10_000L

    /**
     * The smallest power-of-two sample size that brings [width] x [height] within
     * [maxWidth] x [maxHeight]. Powers of two are what the decoders handle natively.
     */
    fun sampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        var sample = 1
        while (width / sample > maxWidth || height / sample > maxHeight) sample *= 2
        return sample
    }

    /**
     * Whether a result from the renderer may be trusted: sane, within what was asked
     * for, and exactly as many bytes as it claims. A compromised renderer could lie.
     */
    fun isValidResult(width: Int, height: Int, byteCount: Int, maxWidth: Int, maxHeight: Int): Boolean =
        width in 1..maxWidth &&
            height in 1..maxHeight &&
            byteCount.toLong() == width.toLong() * height * BYTES_PER_PIXEL
}

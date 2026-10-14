package dev.ikram.sandbox.renderer;

import dev.ikram.sandbox.renderer.ImageResult;

/**
 * Runs in the isolated :renderer process. Every file arrives as a read-only descriptor
 * the app opened; pixels go back in SharedMemory, never as Bitmap objects.
 */
interface IRenderer {
    /**
     * Decodes an image, downsampled to fit maxWidth x maxHeight. Null when the bytes are
     * not an image the platform can decode, or it is larger than the renderer allows.
     */
    @nullable ImageResult decodeImage(in ParcelFileDescriptor fd, int maxWidth, int maxHeight);
}

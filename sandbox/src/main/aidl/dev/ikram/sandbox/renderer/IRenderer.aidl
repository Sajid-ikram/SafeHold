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

    /** Number of pages, or -1 when the bytes are not a PDF the platform can open. */
    int pdfPageCount(in ParcelFileDescriptor fd);

    /**
     * Renders one page on white, scaled to the given width (smaller if the page is very
     * tall). Null when the page cannot be rendered.
     */
    @nullable ImageResult renderPdfPage(in ParcelFileDescriptor fd, int pageIndex, int width);
}

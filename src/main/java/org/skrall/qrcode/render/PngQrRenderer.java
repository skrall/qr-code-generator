package org.skrall.qrcode.render;

import com.google.zxing.common.BitMatrix;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Renders a QR matrix to a PNG with Java2D.
 *
 * <p>The geometry is {@link QrPainter}'s, drawn on a {@link Java2dCanvas}; {@link PdfQrRenderer}
 * draws the same geometry on a PDF page.
 */
@Component
public class PngQrRenderer {

    public byte[] render(BitMatrix matrix, RenderOptions options) {
        return render(matrix, options, null);
    }

    /**
     * Encodes the code as a PNG.
     *
     * @param dpi the print resolution to record in a {@code pHYs} chunk, or {@code null} for none.
     *            It changes how large a print or layout application places the image, never
     *            its pixels.
     */
    public byte[] render(BitMatrix matrix, RenderOptions options, Integer dpi) {
        BufferedImage image = draw(matrix, options);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode the rendered image as PNG.", e);
        }
        return dpi == null ? out.toByteArray() : PngResolution.withDpi(out.toByteArray(), dpi);
    }

    /** Draws the code into pixels: what {@link #render} encodes, and what the scan check reads. */
    public BufferedImage draw(BitMatrix matrix, RenderOptions options) {
        BufferedImage image =
                new BufferedImage(options.width(), options.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            QrPainter.paint(new Java2dCanvas(graphics, options.size()), matrix, options);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}

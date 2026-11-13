package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.DoubleSummaryStatistics;
import javax.imageio.ImageIO;

/**
 * Pixel helpers shared by the images the renderers place on the code.
 */
final class Images {

    /**
     * The minimum luminance distance, on a 0–255 scale, between an ink and what it is drawn on.
     *
     * <p>Shared by the finder artwork, the background image wash and the gradient check, so one
     * threshold decides what counts as readable.
     */
    static final int MIN_CONTRAST = 96;

    private Images() {
    }

    /**
     * Composites an image over a colour, so transparent pixels become that colour.
     *
     * <p>Flattening first means a later contrast pass sees the colour that will actually be
     * drawn, rather than a semi-transparent pixel whose result depends on what is underneath.
     */
    static BufferedImage flatten(BufferedImage source, Color background) {
        BufferedImage flattened =
                new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = flattened.createGraphics();
        try {
            graphics.setColor(background);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return flattened;
    }

    static double luminance(Color color) {
        return luminance(color.getRed(), color.getGreen(), color.getBlue());
    }

    static double luminance(int red, int green, int blue) {
        return 0.299 * red + 0.587 * green + 0.114 * blue;
    }

    /** The luminance range of an image's pixels, ignoring alpha. */
    static DoubleSummaryStatistics luminances(BufferedImage image) {
        DoubleSummaryStatistics statistics = new DoubleSummaryStatistics();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                statistics.accept(luminance((argb >>> 16) & 0xff, (argb >>> 8) & 0xff, argb & 0xff));
            }
        }
        return statistics;
    }

    static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    static String pngDataUrl(BufferedImage image, String failure) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(failure, e);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}

package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.skrall.qrcode.FinderArtworkException;
import org.skrall.qrcode.model.QrFinderArtwork;

/**
 * Decoded finder artwork, adjusted so the finder rings stay readable.
 *
 * <p>The image fills the standard square finder: the outer ring and the 3×3 centre, with the
 * one-module gap left in the background colour. That keeps the 1:1:3:1:1 structure a reader looks
 * for; only the colour inside the dark rings comes from the artwork.
 *
 * <p>Because the artwork is arbitrary, its light pixels could otherwise erase the rings. Every
 * pixel is therefore pushed to a luminance that contrasts with the background by at least
 * {@link #MIN_CONTRAST}. The adjusted image is also what the SVG renderer embeds, so both formats
 * show the same artwork.
 *
 * @param image   the adjusted pixels, used by the PNG renderer
 * @param dataUrl a PNG data URL of the adjusted image, embedded by the SVG renderer
 */
public record FinderArtworkImage(BufferedImage image, String dataUrl) {

    /** The artwork is only seven modules wide, so a larger source gains nothing. */
    private static final int MAX_EMBEDDED_SIDE = 512;

    /** Minimum luminance distance from the background, on a 0–255 scale. */
    private static final int MIN_CONTRAST = 96;

    public static FinderArtworkImage decode(
            QrFinderArtwork artwork, Color finderColor, Color background) {

        if (artwork == null) {
            return null;
        }

        BufferedImage source = ImageDataUrl.decode(
                artwork.dataUrl(), "style.finderArtwork", "finder artwork", FinderArtworkException::new);
        BufferedImage adjusted = adjust(flatten(fit(source), finderColor), background);
        return new FinderArtworkImage(adjusted, dataUrl(adjusted));
    }

    /** Shrinks a large source to something worth embedding; the eye is only seven modules wide. */
    private static BufferedImage fit(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) MAX_EMBEDDED_SIDE / Math.max(width, height));
        if (scale >= 1.0) {
            return source;
        }

        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    /**
     * Composites the artwork over the corner colour, so transparent pixels become that colour.
     *
     * <p>Flattening first means the contrast pass sees the colour that will actually be drawn,
     * rather than a semi-transparent pixel whose result depends on what is underneath.
     */
    private static BufferedImage flatten(BufferedImage source, Color finderColor) {
        BufferedImage flattened =
                new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = flattened.createGraphics();
        try {
            graphics.setColor(finderColor);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return flattened;
    }

    /**
     * Pushes every pixel far enough from the background to keep the rings distinct.
     *
     * <p>On a light background the pixels are darkened, preserving hue; on a dark background they
     * are lightened towards white. The image is already opaque, so every drawn pixel is covered.
     */
    private static BufferedImage adjust(BufferedImage source, Color background) {
        double backgroundLuminance =
                luminance(background.getRed(), background.getGreen(), background.getBlue());
        boolean lightBackground = backgroundLuminance >= 128;

        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage adjusted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int red = (argb >>> 16) & 0xff;
                int green = (argb >>> 8) & 0xff;
                int blue = argb & 0xff;

                double pixelLuminance = luminance(red, green, blue);
                if (lightBackground) {
                    double target = Math.max(0, backgroundLuminance - MIN_CONTRAST);
                    if (pixelLuminance > target) {
                        double factor = pixelLuminance == 0 ? 0 : target / pixelLuminance;
                        red = clamp(red * factor);
                        green = clamp(green * factor);
                        blue = clamp(blue * factor);
                    }
                } else {
                    double target = Math.min(255, backgroundLuminance + MIN_CONTRAST);
                    if (pixelLuminance < target) {
                        double factor = (target - pixelLuminance) / Math.max(1, 255 - pixelLuminance);
                        red = clamp(red + (255 - red) * factor);
                        green = clamp(green + (255 - green) * factor);
                        blue = clamp(blue + (255 - blue) * factor);
                    }
                }

                adjusted.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return adjusted;
    }

    private static double luminance(int red, int green, int blue) {
        return 0.299 * red + 0.587 * green + 0.114 * blue;
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static String dataUrl(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode the finder artwork.", e);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}

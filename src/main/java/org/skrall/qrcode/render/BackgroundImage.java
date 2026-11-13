package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.DoubleSummaryStatistics;
import org.skrall.qrcode.BackgroundImageException;
import org.skrall.qrcode.model.QrBackgroundImage;

/**
 * A decoded background image, washed so the code stays readable on top.
 *
 * <p>The image is cropped to a square and composited over the background colour. Every pixel is
 * then blended toward that colour until it sits at least {@link #MIN_CONTRAST} away from every
 * ink drawn on top: the modules, the eyes and any corner artwork. A dark photograph therefore
 * becomes a pale wash rather than swallowing the code. The ink nearest the background sets the
 * bar, so a light corner colour or light artwork leaves less of the image visible.
 *
 * <p>The washed image is also what the SVG renderer embeds, so both formats show the same
 * pixels.
 *
 * @param image   the washed square image, used by the PNG renderer
 * @param dataUrl a PNG data URL of that image, embedded by the SVG renderer
 */
public record BackgroundImage(BufferedImage image, String dataUrl) {

    /** The output may be 4096 px; a larger embedded image would only bloat the SVG. */
    private static final int MAX_EMBEDDED_SIDE = 1024;

    /** Minimum luminance distance from the foreground, on a 0–255 scale. */
    private static final int MIN_CONTRAST = 96;

    /**
     * @param foreground decides the direction: the image is lightened when the background is
     *                   lighter than the modules, and darkened otherwise
     * @param inks       the luminances of everything drawn over the image
     */
    public static BackgroundImage decode(
            QrBackgroundImage source,
            Color foreground,
            Color background,
            DoubleSummaryStatistics inks,
            int size) {

        if (source == null) {
            return null;
        }

        BufferedImage decoded = ImageDataUrl.decode(
                source.dataUrl(), "style.backgroundImage", "background image",
                BackgroundImageException::new);
        int side = Math.min(size, MAX_EMBEDDED_SIDE);
        side = Math.min(side, Math.max(decoded.getWidth(), decoded.getHeight()));
        BufferedImage washed = wash(cover(decoded, Math.max(1, side)), foreground, background, inks);
        return new BackgroundImage(
                washed, Images.pngDataUrl(washed, "Could not encode the background image."));
    }

    /** Crops the image to a square, centred, so both renderers can stretch it to the canvas. */
    private static BufferedImage cover(BufferedImage source, int side) {
        BufferedImage covered = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = covered.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.max((double) side / source.getWidth(), (double) side / source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            graphics.drawImage(source, (side - width) / 2, (side - height) / 2, width, height, null);
        } finally {
            graphics.dispose();
        }
        return covered;
    }

    /**
     * Blends every pixel toward the background colour until it contrasts with every ink.
     *
     * <p>When the background is lighter than the modules, pixels are pushed up to a minimum
     * luminance above the lightest ink; when it is darker, they are pushed down to a maximum
     * below the darkest one. Pixels that already
     * contrast are left alone, so a bright, low-contrast image keeps its colour and only the
     * troublesome parts are washed out.
     */
    private static BufferedImage wash(
            BufferedImage source, Color foreground, Color background, DoubleSummaryStatistics inks) {
        BufferedImage flat = Images.flatten(source, background);
        double backgroundLuminance = Images.luminance(background);
        boolean lighten = backgroundLuminance >= Images.luminance(foreground);
        double target = lighten
                ? Math.min(255, inks.getMax() + MIN_CONTRAST)
                : Math.max(0, inks.getMin() - MIN_CONTRAST);

        int width = flat.getWidth();
        int height = flat.getHeight();
        BufferedImage washed = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = flat.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int red = (argb >>> 16) & 0xff;
                int green = (argb >>> 8) & 0xff;
                int blue = argb & 0xff;

                double pixelLuminance = Images.luminance(red, green, blue);
                double blend = 0;
                if (lighten && pixelLuminance < target) {
                    blend = backgroundLuminance > pixelLuminance
                            ? (target - pixelLuminance) / (backgroundLuminance - pixelLuminance)
                            : 1;
                } else if (!lighten && pixelLuminance > target) {
                    blend = pixelLuminance > backgroundLuminance
                            ? (pixelLuminance - target) / (pixelLuminance - backgroundLuminance)
                            : 1;
                }
                blend = Math.max(0, Math.min(1, blend));

                red = Images.clamp(red + (background.getRed() - red) * blend);
                green = Images.clamp(green + (background.getGreen() - green) * blend);
                blue = Images.clamp(blue + (background.getBlue() - blue) * blend);
                washed.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return washed;
    }
}

package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
 * {@link Images#MIN_CONTRAST}. The adjusted image is also what the SVG renderer embeds, so both
 * formats show the same artwork.
 *
 * @param image   the adjusted pixels, used by the PNG renderer
 * @param dataUrl a PNG data URL of the adjusted image, embedded by the SVG renderer
 */
public record FinderArtworkImage(BufferedImage image, String dataUrl) {

    /** The artwork is only seven modules wide, so a larger source gains nothing. */
    private static final int MAX_EMBEDDED_SIDE = 512;


    /**
     * @param modules the modules' colour (the gradient's start when there is one): the artwork is
     *                pushed to the same side of the background as the modules
     */
    public static FinderArtworkImage decode(
            QrFinderArtwork artwork, Color finderColor, Color background, Color modules) {

        if (artwork == null) {
            return null;
        }

        BufferedImage source = ImageDataUrl.decode(
                artwork.dataUrl(), "style.finderArtwork", "finder artwork", FinderArtworkException::new);
        BufferedImage adjusted = adjust(Images.flatten(fit(source), finderColor), background, modules);
        return new FinderArtworkImage(
                adjusted, Images.pngDataUrl(adjusted, "Could not encode the finder artwork."));
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
     * Pushes every pixel far enough from the background to keep the rings distinct.
     *
     * <p>When the modules are darker than the background the pixels are darkened, preserving hue;
     * when they are lighter (an inverted code) the pixels are lightened towards white. The rings
     * must sit on the same side of the background as the modules, or a reader sees eyes that do
     * not match the code. Only when the modules match the background, which no reader can scan
     * anyway, does the background's own brightness decide. The image is already opaque, so every
     * drawn pixel is covered.
     */
    private static BufferedImage adjust(BufferedImage source, Color background, Color modules) {
        double backgroundLuminance = Images.luminance(background);
        double modulesLuminance = Images.luminance(modules);
        boolean lightBackground = modulesLuminance == backgroundLuminance
                ? backgroundLuminance >= 128
                : modulesLuminance < backgroundLuminance;

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

                double pixelLuminance = Images.luminance(red, green, blue);
                if (lightBackground) {
                    double target = Math.max(0, backgroundLuminance - Images.MIN_CONTRAST);
                    if (pixelLuminance > target) {
                        double factor = pixelLuminance == 0 ? 0 : target / pixelLuminance;
                        red = Images.clamp(red * factor);
                        green = Images.clamp(green * factor);
                        blue = Images.clamp(blue * factor);
                    }
                } else {
                    double target = Math.min(255, backgroundLuminance + Images.MIN_CONTRAST);
                    if (pixelLuminance < target) {
                        double factor = (target - pixelLuminance) / Math.max(1, 255 - pixelLuminance);
                        red = Images.clamp(red + (255 - red) * factor);
                        green = Images.clamp(green + (255 - green) * factor);
                        blue = Images.clamp(blue + (255 - blue) * factor);
                    }
                }

                adjusted.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return adjusted;
    }
}

package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.skrall.qrcode.LogoException;
import org.skrall.qrcode.model.QrLogo;

/**
 * A decoded logo, ready to draw.
 *
 * <p>The image is decoded once even for SVG output, where only the data URL is embedded: decoding
 * is what proves the payload really is an image, and it keeps a decompression bomb from being
 * handed to a browser instead.
 *
 * @param image          the decoded pixels, used by the PNG renderer
 * @param dataUrl        the original data URL, embedded by the SVG renderer
 * @param sizePercent    the logo's width as a percentage of the image width
 * @param paddingPercent the light plate's padding as a percentage of the logo width
 * @param radiusPercent  the plate's corner radius as a percentage of the plate width
 * @param background     the plate's colour
 */
public record LogoImage(
        BufferedImage image,
        String dataUrl,
        int sizePercent,
        int paddingPercent,
        int radiusPercent,
        Color background) {

    private static final int DEFAULT_SIZE_PERCENT = 20;
    private static final int DEFAULT_PADDING_PERCENT = 8;
    private static final int DEFAULT_RADIUS_PERCENT = 25;

    public static LogoImage decode(QrLogo logo) {
        if (logo == null) {
            return null;
        }

        BufferedImage image = ImageDataUrl.decode(
                logo.dataUrl(), "logo", "logo", LogoException::new);

        return new LogoImage(
                image,
                logo.dataUrl(),
                logo.sizePercent() == null ? DEFAULT_SIZE_PERCENT : logo.sizePercent(),
                logo.paddingPercent() == null ? DEFAULT_PADDING_PERCENT : logo.paddingPercent(),
                logo.radiusPercent() == null ? DEFAULT_RADIUS_PERCENT : logo.radiusPercent(),
                parse(logo.background()));
    }

    private static Color parse(String hex) {
        if (hex == null) {
            return Color.WHITE;
        }
        return new Color(Integer.parseInt(hex.substring(1), 16));
    }
}

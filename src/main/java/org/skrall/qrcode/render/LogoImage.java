package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import javax.imageio.ImageIO;
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

    /** Decoded bytes, well below the 3 MB of base64 the DTO allows. */
    private static final int MAX_BYTES = 2_000_000;

    /** Either side of the source image. */
    private static final int MAX_SIDE = 4096;

    /** Total source pixels, which catches a small file that expands to a huge bitmap. */
    private static final long MAX_PIXELS = 16_000_000L;

    private static final String PREFIX = "data:image/";

    private static final int DEFAULT_SIZE_PERCENT = 20;
    private static final int DEFAULT_PADDING_PERCENT = 8;
    private static final int DEFAULT_RADIUS_PERCENT = 25;

    public static LogoImage decode(QrLogo logo) {
        if (logo == null) {
            return null;
        }

        String dataUrl = logo.dataUrl();
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.startsWith(PREFIX)) {
            throw new LogoException("logo.dataUrl must be a base64 data URL such as data:image/png;base64,...");
        }
        String metadata = dataUrl.substring(PREFIX.length(), comma).toLowerCase(Locale.ROOT);
        if (!metadata.endsWith(";base64")) {
            throw new LogoException("logo.dataUrl must carry a base64 payload.");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw new LogoException("logo.dataUrl does not contain valid base64.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new LogoException("The decoded logo must be at most 2 MB.");
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new LogoException("logo.dataUrl could not be read as an image.");
        }
        if (image == null) {
            throw new LogoException("The logo must be a PNG, JPEG or GIF image.");
        }
        if (image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE
                || (long) image.getWidth() * image.getHeight() > MAX_PIXELS) {
            throw new LogoException("The logo must be at most " + MAX_SIDE + " pixels on either side.");
        }

        return new LogoImage(
                image,
                dataUrl,
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

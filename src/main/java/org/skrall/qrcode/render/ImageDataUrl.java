package org.skrall.qrcode.render;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Function;
import javax.imageio.ImageIO;

/**
 * Decodes a base64 image data URL with the limits the renderers rely on.
 *
 * <p>A logo and the finder artwork accept the same formats and have the same size limits; only
 * the wording of a rejection differs, so the caller supplies the field name, the noun used in
 * messages and the exception to throw.
 */
final class ImageDataUrl {

    /** Decoded bytes, well below the 3 MB of base64 the DTOs allow. */
    private static final int MAX_BYTES = 2_000_000;

    /** Either side of the source image. */
    private static final int MAX_SIDE = 4096;

    /** Total source pixels, which catches a small file that expands to a huge bitmap. */
    private static final long MAX_PIXELS = 16_000_000L;

    private static final String PREFIX = "data:image/";

    private ImageDataUrl() {
    }

    /**
     * @param dataUrl the {@code data:image/...;base64,...} URL
     * @param field   the request field, used to prefix messages
     * @param noun    what the image is, such as {@code logo}, used in messages
     * @param error   builds the exception to throw
     */
    static BufferedImage decode(
            String dataUrl,
            String field,
            String noun,
            Function<String, RuntimeException> error) {

        if (dataUrl == null || dataUrl.isBlank()) {
            throw error.apply(field + ".dataUrl must not be blank.");
        }

        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.startsWith(PREFIX)) {
            throw error.apply(field + ".dataUrl must be a base64 data URL such as data:image/png;base64,...");
        }
        String metadata = dataUrl.substring(PREFIX.length(), comma).toLowerCase(Locale.ROOT);
        if (!metadata.endsWith(";base64")) {
            throw error.apply(field + ".dataUrl must carry a base64 payload.");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw error.apply(field + ".dataUrl does not contain valid base64.");
        }
        if (bytes.length > MAX_BYTES) {
            throw error.apply("The decoded " + noun + " must be at most 2 MB.");
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw error.apply(field + ".dataUrl could not be read as an image.");
        }
        if (image == null) {
            throw error.apply("The " + noun + " must be a PNG, JPEG or GIF image.");
        }
        if (image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE
                || (long) image.getWidth() * image.getHeight() > MAX_PIXELS) {
            throw error.apply("The " + noun + " must be at most " + MAX_SIDE + " pixels on either side.");
        }
        return image;
    }
}

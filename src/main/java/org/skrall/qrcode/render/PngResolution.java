package org.skrall.qrcode.render;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Records a print resolution in a PNG, as a {@code pHYs} chunk.
 *
 * <p>The chunk is spliced in after {@code IHDR} rather than written through ImageIO's metadata
 * tree: the pixels and every other byte stay exactly as encoded, and nothing new has to be made
 * reachable for the native image.
 */
final class PngResolution {

    /** The signature (8 bytes) and the IHDR chunk (length, type, 13 bytes of data, CRC). */
    private static final int AFTER_IHDR = 8 + 4 + 4 + 13 + 4;

    private static final double METRES_PER_INCH = 0.0254;

    private PngResolution() {
    }

    /** The PNG with a {@code pHYs} chunk saying {@code dpi} in both directions. */
    static byte[] withDpi(byte[] png, int dpi) {
        if (png.length < AFTER_IHDR
                || !"IHDR".equals(new String(png, 12, 4, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Not a PNG that starts with IHDR");
        }
        long perMetre = Math.round(dpi / METRES_PER_INCH);

        ByteBuffer chunk = ByteBuffer.allocate(4 + 4 + 9 + 4);
        chunk.putInt(9);
        chunk.put("pHYs".getBytes(StandardCharsets.US_ASCII));
        chunk.putInt((int) perMetre);
        chunk.putInt((int) perMetre);
        chunk.put((byte) 1); // the unit is the metre
        CRC32 crc = new CRC32();
        crc.update(chunk.array(), 4, 4 + 9);
        chunk.putInt((int) crc.getValue());

        byte[] out = new byte[png.length + chunk.capacity()];
        System.arraycopy(png, 0, out, 0, AFTER_IHDR);
        System.arraycopy(chunk.array(), 0, out, AFTER_IHDR, chunk.capacity());
        System.arraycopy(png, AFTER_IHDR, out, AFTER_IHDR + chunk.capacity(), png.length - AFTER_IHDR);
        return out;
    }
}

package org.skrall.qrcode.render;

import java.awt.image.BufferedImage;

/**
 * Embeds a decoded image in a PDF as an image XObject.
 *
 * <p>The pixels are written as 8-bit RGB, compressed with Flate after PNG row filters (the
 * {@code /Predictor 15} decode parameter), with the alpha channel as a separate soft mask when
 * any pixel is not opaque. The image is embedded at its own resolution: a logo keeps every pixel
 * it was uploaded with, however small it is drawn.
 */
final class PdfImages {

    private PdfImages() {
    }

    /** Adds the image (and its mask) to the document and returns the image's object number. */
    static int embed(PdfDocument document, BufferedImage image, boolean interpolate) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

        byte[] rgb = new byte[width * height * 3];
        byte[] alpha = new byte[width * height];
        boolean translucent = false;
        for (int i = 0; i < argb.length; i++) {
            int pixel = argb[i];
            rgb[i * 3] = (byte) (pixel >>> 16);
            rgb[i * 3 + 1] = (byte) (pixel >>> 8);
            rgb[i * 3 + 2] = (byte) pixel;
            alpha[i] = (byte) (pixel >>> 24);
            translucent |= (pixel >>> 24) != 0xff;
        }

        String common = "/Type /XObject /Subtype /Image /Width " + width + " /Height " + height
                + " /BitsPerComponent 8" + (interpolate ? " /Interpolate true" : "");
        String mask = "";
        if (translucent) {
            int maskNumber = document.addFlateStream(common + " /ColorSpace /DeviceGray"
                    + decodeParms(1, width), predict(alpha, width, height, 1));
            mask = " /SMask " + maskNumber + " 0 R";
        }
        return document.addFlateStream(common + " /ColorSpace /DeviceRGB" + decodeParms(3, width) + mask,
                predict(rgb, width, height, 3));
    }

    private static String decodeParms(int colours, int width) {
        return " /DecodeParms << /Predictor 15 /Colors " + colours
                + " /BitsPerComponent 8 /Columns " + width + " >>";
    }

    /**
     * Applies a PNG filter to every row, each row choosing whichever of the five filters leaves
     * the smallest sum of absolute values, the usual heuristic for what deflates best.
     *
     * @param bytesPerPixel 3 for RGB, 1 for a grey mask
     */
    static byte[] predict(byte[] pixels, int width, int height, int bytesPerPixel) {
        int stride = width * bytesPerPixel;
        byte[] out = new byte[height * (stride + 1)];
        byte[] candidate = new byte[stride];
        byte[] best = new byte[stride];
        for (int y = 0; y < height; y++) {
            int row = y * stride;
            int previous = row - stride;
            long bestScore = Long.MAX_VALUE;
            int bestFilter = 0;
            for (int filter = 0; filter <= 4; filter++) {
                long score = 0;
                for (int i = 0; i < stride; i++) {
                    int raw = pixels[row + i] & 0xff;
                    int left = i >= bytesPerPixel ? pixels[row + i - bytesPerPixel] & 0xff : 0;
                    int up = y > 0 ? pixels[previous + i] & 0xff : 0;
                    int upLeft = y > 0 && i >= bytesPerPixel ? pixels[previous + i - bytesPerPixel] & 0xff : 0;
                    int predicted = switch (filter) {
                        case 0 -> 0;
                        case 1 -> left;
                        case 2 -> up;
                        case 3 -> (left + up) >>> 1;
                        default -> paeth(left, up, upLeft);
                    };
                    byte value = (byte) (raw - predicted);
                    candidate[i] = value;
                    score += Math.abs(value);
                }
                if (score < bestScore) {
                    bestScore = score;
                    bestFilter = filter;
                    System.arraycopy(candidate, 0, best, 0, stride);
                }
            }
            int at = y * (stride + 1);
            out[at] = (byte) bestFilter;
            System.arraycopy(best, 0, out, at + 1, stride);
        }
        return out;
    }

    private static int paeth(int left, int up, int upLeft) {
        int estimate = left + up - upLeft;
        int toLeft = Math.abs(estimate - left);
        int toUp = Math.abs(estimate - up);
        int toUpLeft = Math.abs(estimate - upLeft);
        if (toLeft <= toUp && toLeft <= toUpLeft) {
            return left;
        }
        return toUp <= toUpLeft ? up : upLeft;
    }
}

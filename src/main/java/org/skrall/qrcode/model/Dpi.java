package org.skrall.qrcode.model;

import org.skrall.qrcode.DpiException;

/**
 * The print resolution: how many of the image's pixels make an inch on paper.
 *
 * <p>It is a property of the output rather than of the design, like the format, so it is a query
 * parameter of the render endpoint and an option of a batch, never part of a style. A PDF's page
 * size comes from it; a PNG records it in a {@code pHYs} chunk; an SVG ignores it.
 */
public final class Dpi {

    public static final int MIN = 72;
    public static final int MAX = 1200;

    /** What a PDF is sized at when no resolution is given. */
    public static final int DEFAULT = 300;

    public static final String RANGE_MESSAGE = "dpi must be between 72 and 1200";

    private Dpi() {
    }

    /**
     * Checks a requested resolution.
     *
     * @return the resolution, or {@code null} when none was requested
     * @throws DpiException when it is out of range
     */
    public static Integer check(Integer dpi) {
        if (dpi != null && (dpi < MIN || dpi > MAX)) {
            throw new DpiException(RANGE_MESSAGE + ", not " + dpi + ".");
        }
        return dpi;
    }

    /** The resolution a PDF's page is sized at. */
    public static int forPdf(Integer dpi) {
        return dpi == null ? DEFAULT : dpi;
    }
}

package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * What {@link QrPainter} draws on: Java2D pixels for the PNG, or a PDF page.
 *
 * <p>The painter decides every shape and every position; a canvas only fills what it is given.
 * That keeps the PNG and the PDF on one geometry rather than two that mirror each other. Shapes
 * are in pixels with y pointing down, as in Java2D.
 *
 * <p>There is no "clear" operation. A PDF has no way to erase what was painted, so the one place
 * the PNG clears pixels, the eye's gap over a transparent background, is expressed as a ring
 * instead: {@link #fillRing} and {@link #drawImageInRing}. Each canvas does that its own way.
 */
interface Canvas {

    /** Moves the origin by whole pixels; it stays moved for everything drawn after. */
    void translate(int x, int y);

    /** Fills a shape with a flat colour or with the gradient, honouring its winding rule. */
    void fill(Shape shape, Ink ink);

    /** Fills {@code outer} but leaves {@code hole}, which lies inside it, unpainted. */
    void fillRing(Shape outer, Shape hole, Ink ink);

    /** From now on, images are scaled with bilinear interpolation rather than the default. */
    void interpolateImages();

    /** Draws an image scaled into the rectangle. */
    void drawImage(BufferedImage image, int x, int y, int width, int height);

    /** Draws an image scaled into the rectangle, bilinearly, clipped to {@code clip}. */
    void drawImage(BufferedImage image, Shape clip, int x, int y, int width, int height);

    /**
     * Draws an image as {@link #drawImage(BufferedImage, Shape, int, int, int, int)} clipped to
     * {@code outer}, then leaves {@code hole} unpainted.
     */
    void drawImageInRing(BufferedImage image, Shape outer, Shape hole, int x, int y, int width, int height);

    /**
     * A fill: a flat colour, or a gradient spanning the code's square in user space.
     *
     * @param color    the colour, when {@code gradient} is {@code null}
     * @param gradient the gradient, or {@code null}
     */
    record Ink(Color color, Gradient gradient) {

        static Ink of(Color color) {
            return new Ink(color, null);
        }

        static Ink of(Gradient gradient) {
            return new Ink(null, gradient);
        }
    }
}

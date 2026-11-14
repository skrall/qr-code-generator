package org.skrall.qrcode.render;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * The PNG's canvas: plain Java2D on an ARGB image.
 *
 * <p>A ring is the outer shape filled and the hole then cleared with {@link AlphaComposite#Clear},
 * which is how the PNG has always drawn an eye over a transparent background.
 */
final class Java2dCanvas implements Canvas {

    private final Graphics2D graphics;
    private final int size;
    private final Map<Gradient, Paint> gradients = new HashMap<>();

    /**
     * @param graphics where to draw, anti-aliased by the caller
     * @param size     the code's side, which a gradient spans
     */
    Java2dCanvas(Graphics2D graphics, int size) {
        this.graphics = graphics;
        this.size = size;
    }

    @Override
    public void translate(int x, int y) {
        graphics.translate(x, y);
    }

    @Override
    public void fill(Shape shape, Ink ink) {
        graphics.setPaint(paint(ink));
        graphics.fill(shape);
    }

    @Override
    public void fillRing(Shape outer, Shape hole, Ink ink) {
        fill(outer, ink);
        clear(hole);
    }

    @Override
    public void interpolateImages() {
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    @Override
    public void drawImage(BufferedImage image, int x, int y, int width, int height) {
        graphics.drawImage(image, x, y, width, height, null);
    }

    @Override
    public void drawImage(BufferedImage image, Shape clip, int x, int y, int width, int height) {
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(clip);
            clipped.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            clipped.drawImage(image, x, y, width, height, null);
        } finally {
            clipped.dispose();
        }
    }

    @Override
    public void drawImageInRing(
            BufferedImage image, Shape outer, Shape hole, int x, int y, int width, int height) {
        drawImage(image, outer, x, y, width, height);
        clear(hole);
    }

    private void clear(Shape shape) {
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fill(shape);
        graphics.setComposite(previous);
    }

    private Paint paint(Ink ink) {
        if (ink.gradient() == null) {
            return ink.color();
        }
        return gradients.computeIfAbsent(ink.gradient(), gradient -> gradient.paint(size));
    }
}

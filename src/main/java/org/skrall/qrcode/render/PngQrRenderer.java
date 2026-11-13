package org.skrall.qrcode.render;

import com.google.zxing.common.BitMatrix;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Renders a QR matrix to a PNG with Java2D.
 *
 * <p>The matrix comes from ZXing, but none of ZXing's writers are used: they can only draw
 * squares, and shaping the modules and the corner patterns is the point of this application.
 */
@Component
public class PngQrRenderer {

    /** The side of a finder pattern, in modules. */
    private static final int FINDER = 7;

    /**
     * Corner radius of a liquid module, as a fraction of the module's side.
     *
     * <p>Kept below a half so the centre of every module stays solid: a reader samples the centre,
     * not the rounded edge.
     */
    private static final double LIQUID_RADIUS = 0.4;

    public byte[] render(BitMatrix matrix, RenderOptions options) {
        int dimension = matrix.getWidth();
        double scale = (double) options.size() / (dimension + options.margin() * 2);

        BufferedImage image = new BufferedImage(options.size(), options.size(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            graphics.setColor(options.background());
            graphics.fillRect(0, 0, options.size(), options.size());

            if (options.backgroundImage() != null) {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(
                        options.backgroundImage().image(), 0, 0, options.size(), options.size(), null);
            }

            graphics.setPaint(modulePaint(options));
            for (int row = 0; row < dimension; row++) {
                for (int column = 0; column < dimension; column++) {
                    if (matrix.get(column, row) && !isFinder(row, column, dimension)) {
                        drawModule(graphics, matrix, column, row, dimension, scale, options);
                    }
                }
            }

            drawEye(graphics, offset(0, options, scale), offset(0, options, scale), scale, options);
            drawEye(graphics, offset(dimension - FINDER, options, scale), offset(0, options, scale), scale, options);
            drawEye(graphics, offset(0, options, scale), offset(dimension - FINDER, options, scale), scale, options);

            drawLogo(graphics, options);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode the rendered image as PNG.", e);
        }
        return out.toByteArray();
    }

    /**
     * Whether a module belongs to one of the three corner patterns.
     *
     * <p>Those are drawn as whole shapes afterwards, so their modules are skipped here rather
     * than being drawn twice.
     */
    private static boolean isFinder(int row, int column, int dimension) {
        return (row < FINDER && column < FINDER)
                || (row < FINDER && column >= dimension - FINDER)
                || (row >= dimension - FINDER && column < FINDER);
    }

    /**
     * Whether a cell is a dark module outside the finder patterns.
     *
     * <p>Finder cells count as light so a liquid bridge never reaches into an eye. The eye is
     * drawn afterwards, and its shape may not cover a protrusion from the module pass.
     */
    private static boolean isDark(BitMatrix matrix, int row, int column, int dimension) {
        return row >= 0 && row < dimension
                && column >= 0 && column < dimension
                && matrix.get(column, row)
                && !isFinder(row, column, dimension);
    }

    /** The flat foreground, or the gradient spread over the whole canvas. */
    private static Paint modulePaint(RenderOptions options) {
        return options.gradient() == null
                ? options.foreground()
                : options.gradient().paint(options.size());
    }

    private static Paint finderPaint(RenderOptions options) {
        return options.gradientFinders()
                ? options.gradient().paint(options.size())
                : options.finderColor();
    }

    private static double offset(int module, RenderOptions options, double scale) {
        return (module + options.margin()) * scale;
    }

    private static void drawModule(
            Graphics2D graphics,
            BitMatrix matrix,
            int column,
            int row,
            int dimension,
            double scale,
            RenderOptions options) {

        double x = (column + options.margin()) * scale;
        double y = (row + options.margin()) * scale;
        switch (options.moduleShape()) {
            case SQUARE -> {
                // Rounded to whole pixels and grown to the next one: two adjacent squares then
                // overlap by at most a pixel instead of leaving a hairline seam between them.
                double left = Math.floor(x);
                double top = Math.floor(y);
                graphics.fill(new Rectangle2D.Double(
                        left, top, Math.ceil(x + scale) - left, Math.ceil(y + scale) - top));
            }
            case ROUNDED -> graphics.fill(
                    new RoundRectangle2D.Double(x, y, scale, scale, scale * 0.6, scale * 0.6));
            case DOTS -> {
                double diameter = scale * 0.86;
                double inset = (scale - diameter) / 2;
                graphics.fill(new Ellipse2D.Double(x + inset, y + inset, diameter, diameter));
            }
            case STAR -> graphics.fill(star(x, y, scale));
            case LIQUID -> drawLiquidModule(graphics, matrix, column, row, dimension, x, y, scale);
        }
    }

    /**
     * A rounded module plus bridges to its right and lower neighbours.
     *
     * <p>Each bridge spans the full height (or width) of the shared edge and reaches a corner
     * radius into both cells. That squares off the shared side, so a run of modules reads as one
     * rounded bar instead of two circles joined by a narrow neck.
     */
    private static void drawLiquidModule(
            Graphics2D graphics,
            BitMatrix matrix,
            int column,
            int row,
            int dimension,
            double x,
            double y,
            double scale) {

        double radius = scale * LIQUID_RADIUS;
        graphics.fill(new RoundRectangle2D.Double(x, y, scale, scale, radius * 2, radius * 2));

        if (isDark(matrix, row, column + 1, dimension)) {
            graphics.fill(new Rectangle2D.Double(x + scale - radius, y, radius * 2, scale));
        }
        if (isDark(matrix, row + 1, column, dimension)) {
            graphics.fill(new Rectangle2D.Double(x, y + scale - radius, scale, radius * 2));
        }
    }

    /**
     * A four-point star whose tips reach the middle of each cell edge.
     *
     * <p>The waist is deliberately wide: a thin sparkle loses too much ink at the module sizes a
     * dense code produces, and the centre has to stay solid for a reader sampling it.
     */
    private static Path2D star(double x, double y, double scale) {
        double centreX = x + scale / 2;
        double centreY = y + scale / 2;
        double outer = scale / 2;
        double inner = scale * 0.36 / Math.sqrt(2);

        Path2D.Double path = new Path2D.Double();
        path.moveTo(centreX, centreY - outer);
        path.lineTo(centreX + inner, centreY - inner);
        path.lineTo(centreX + outer, centreY);
        path.lineTo(centreX + inner, centreY + inner);
        path.lineTo(centreX, centreY + outer);
        path.lineTo(centreX - inner, centreY + inner);
        path.lineTo(centreX - outer, centreY);
        path.lineTo(centreX - inner, centreY - inner);
        path.closePath();
        return path;
    }

    private static void drawEye(Graphics2D graphics, double x, double y, double scale, RenderOptions options) {
        double side = FINDER * scale;
        Paint foreground = finderPaint(options);
        Color background = options.background();
        switch (options.finderShape()) {
            case SQUARE -> {
                graphics.setPaint(foreground);
                graphics.fill(new Rectangle2D.Double(x, y, side, side));
                graphics.setColor(background);
                graphics.fill(new Rectangle2D.Double(x + scale, y + scale, side - 2 * scale, side - 2 * scale));
                graphics.setPaint(foreground);
                graphics.fill(new Rectangle2D.Double(x + 2 * scale, y + 2 * scale, 3 * scale, 3 * scale));
            }
            case ROUNDED -> {
                graphics.setPaint(foreground);
                graphics.fill(rounded(x, y, side, 2.4 * scale));
                graphics.setColor(background);
                graphics.fill(rounded(x + scale, y + scale, side - 2 * scale, 1.6 * scale));
                graphics.setPaint(foreground);
                graphics.fill(rounded(x + 2 * scale, y + 2 * scale, 3 * scale, 0.9 * scale));
            }
            case CIRCLE -> {
                graphics.setPaint(foreground);
                graphics.fill(new Ellipse2D.Double(x, y, side, side));
                graphics.setColor(background);
                graphics.fill(new Ellipse2D.Double(x + scale, y + scale, side - 2 * scale, side - 2 * scale));
                graphics.setPaint(foreground);
                graphics.fill(new Ellipse2D.Double(x + 2 * scale, y + 2 * scale, 3 * scale, 3 * scale));
            }
            case LEAF -> {
                graphics.setPaint(foreground);
                graphics.fill(leaf(x, y, side, 2.4 * scale));
                graphics.setColor(background);
                graphics.fill(leaf(x + scale, y + scale, side - 2 * scale, 1.6 * scale));
                graphics.setPaint(foreground);
                graphics.fill(leaf(x + 2 * scale, y + 2 * scale, 3 * scale, 0.9 * scale));
            }
            case DIAMOND -> {
                graphics.setPaint(foreground);
                graphics.fill(diamond(x, y, side, 0));
                graphics.setColor(background);
                graphics.fill(diamond(x, y, side, scale));
                graphics.setPaint(foreground);
                graphics.fill(diamond(x, y, side, 2 * scale));
            }
            case CUSTOM -> drawCustomEye(graphics, x, y, scale, options);
        }
    }

    /**
     * The standard square eye with the artwork filling its two dark regions.
     *
     * <p>The outer square and the centre are filled with the artwork, then the one-module gap is
     * painted back in the background colour. The ring and centre are always present, so the
     * 1:1:3:1:1 structure survives whatever the image contains. The artwork was already flattened
     * over {@code finderColor}, so transparent pixels are that colour.
     */
    private static void drawCustomEye(
            Graphics2D graphics, double x, double y, double scale, RenderOptions options) {

        FinderArtworkImage artwork = options.finderArtwork();
        if (artwork == null) {
            // RenderOptions already falls back to SQUARE; this only guards a direct caller.
            return;
        }

        double side = FINDER * scale;
        Shape outer = new Rectangle2D.Double(x, y, side, side);
        Shape gap = new Rectangle2D.Double(
                x + scale, y + scale, side - 2 * scale, side - 2 * scale);
        Shape centre = new Rectangle2D.Double(x + 2 * scale, y + 2 * scale, 3 * scale, 3 * scale);

        drawArtwork(graphics, artwork.image(), outer, x, y, side);

        graphics.setColor(options.background());
        graphics.fill(gap);

        drawArtwork(graphics, artwork.image(), centre, x, y, side);
    }

    /** Draws the artwork into the finder footprint, clipped to one of its dark regions. */
    private static void drawArtwork(
            Graphics2D graphics, BufferedImage image, Shape clip, double x, double y, double side) {

        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(clip);
            clipped.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double fit = Math.min(side / image.getWidth(), side / image.getHeight());
            int width = (int) Math.round(image.getWidth() * fit);
            int height = (int) Math.round(image.getHeight() * fit);
            int left = (int) Math.round(x + (side - width) / 2.0);
            int top = (int) Math.round(y + (side - height) / 2.0);
            clipped.drawImage(image, left, top, width, height, null);
        } finally {
            clipped.dispose();
        }
    }

    private static RoundRectangle2D rounded(double x, double y, double side, double radius) {
        return new RoundRectangle2D.Double(x, y, side, side, radius * 2, radius * 2);
    }

    /** A square with its top-left and bottom-right corners rounded. */
    private static Path2D leaf(double x, double y, double side, double radius) {
        double r = Math.min(radius, side / 2);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(x + r, y);
        path.lineTo(x + side, y);
        path.lineTo(x + side, y + side - r);
        path.quadTo(x + side, y + side, x + side - r, y + side);
        path.lineTo(x, y + side);
        path.lineTo(x, y + r);
        path.quadTo(x, y, x + r, y);
        path.closePath();
        return path;
    }

    /** A square turned a quarter turn, inset from the same bounding box as the other eyes. */
    private static Path2D diamond(double x, double y, double side, double inset) {
        double centreX = x + side / 2;
        double centreY = y + side / 2;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(centreX, y + inset);
        path.lineTo(x + side - inset, centreY);
        path.lineTo(centreX, y + side - inset);
        path.lineTo(x + inset, centreY);
        path.closePath();
        return path;
    }

    private static void drawLogo(Graphics2D graphics, RenderOptions options) {
        LogoImage logo = options.logo();
        if (logo == null) {
            return;
        }

        double side = options.size() * logo.sizePercent() / 100.0;
        double padding = side * logo.paddingPercent() / 100.0;
        double plate = side + padding * 2;
        double left = (options.size() - plate) / 2;
        double radius = plate * logo.radiusPercent() / 100.0;

        graphics.setColor(logo.background());
        graphics.fill(new RoundRectangle2D.Double(left, left, plate, plate, radius * 2, radius * 2));

        BufferedImage image = logo.image();
        double fit = Math.min(side / image.getWidth(), side / image.getHeight());
        int width = (int) Math.round(image.getWidth() * fit);
        int height = (int) Math.round(image.getHeight() * fit);
        graphics.drawImage(image,
                (options.size() - width) / 2,
                (options.size() - height) / 2,
                width,
                height,
                null);
    }
}

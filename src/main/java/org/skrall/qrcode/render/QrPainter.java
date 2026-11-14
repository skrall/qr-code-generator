package org.skrall.qrcode.render;

import com.google.zxing.common.BitMatrix;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import org.skrall.qrcode.render.Canvas.Ink;

/**
 * The code's geometry, drawn onto a {@link Canvas}: the PNG's pixels and the PDF's page.
 *
 * <p>Every shape and position is decided here, so the two formats cannot drift apart.
 * {@link SvgQrRenderer} writes the same geometry as strings and mirrors this class.
 *
 * <p>The matrix comes from ZXing, but none of ZXing's writers are used: they can only draw
 * squares, and shaping the modules and the corner patterns is the point of this application.
 */
final class QrPainter {

    /** The side of a finder pattern, in modules. */
    private static final int FINDER = 7;

    /**
     * Corner radius of a liquid module, as a fraction of the module's side.
     *
     * <p>Kept below a half so the centre of every module stays solid: a reader samples the centre,
     * not the rounded edge.
     */
    private static final double LIQUID_RADIUS = 0.4;

    private QrPainter() {
    }

    static void paint(Canvas canvas, BitMatrix matrix, RenderOptions options) {
        int dimension = matrix.getWidth();
        double scale = (double) options.size() / (dimension + options.margin() * 2);

        if (options.frame() != null) {
            paintFrame(canvas, options.frame(), options.transparentBackground());
            // The code is drawn in its own coordinates, as when unframed. The offset is whole
            // pixels, so the modules land on the same grid, and the gradient moves too.
            canvas.translate(options.frame().codeX(), options.frame().codeY());
        }

        // A transparent background is simply never painted: the PNG starts with every pixel at
        // alpha 0, and a PDF page is transparent until something is drawn on it.
        if (!options.transparentBackground()) {
            canvas.fill(new Rectangle2D.Double(0, 0, options.size(), options.size()),
                    Ink.of(options.background()));
        }

        if (options.backgroundImage() != null) {
            canvas.interpolateImages();
            canvas.drawImage(options.backgroundImage().image(), 0, 0, options.size(), options.size());
        }

        Ink moduleInk = options.gradient() == null
                ? Ink.of(options.foreground())
                : Ink.of(options.gradient());
        for (int row = 0; row < dimension; row++) {
            for (int column = 0; column < dimension; column++) {
                if (matrix.get(column, row) && !isFinder(row, column, dimension)) {
                    paintModule(canvas, moduleInk, matrix, column, row, dimension, scale, options);
                }
            }
        }

        paintEye(canvas, offset(0, options, scale), offset(0, options, scale), scale, options);
        paintEye(canvas, offset(dimension - FINDER, options, scale), offset(0, options, scale), scale, options);
        paintEye(canvas, offset(0, options, scale), offset(dimension - FINDER, options, scale), scale, options);

        paintLogo(canvas, options);
    }

    /**
     * The card, its border and its text; the shapes come from {@link Frame}, as in the SVG. A
     * transparent background leaves the card and bleed unpainted.
     */
    private static void paintFrame(Canvas canvas, Frame frame, boolean transparent) {
        if (!transparent) {
            canvas.fill(new Rectangle2D.Double(0, 0, frame.width(), frame.height()),
                    Ink.of(frame.background()));
        }
        if (frame.border() != null) {
            canvas.fill(frame.border(), Ink.of(frame.borderColor()));
        }
        if (frame.text() != null) {
            canvas.fill(frame.text(), Ink.of(frame.textColor()));
        }
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

    private static Ink finderInk(RenderOptions options) {
        return options.gradientFinders()
                ? Ink.of(options.gradient())
                : Ink.of(options.finderColor());
    }

    private static double offset(int module, RenderOptions options, double scale) {
        return (module + options.margin()) * scale;
    }

    private static void paintModule(
            Canvas canvas,
            Ink ink,
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
                canvas.fill(new Rectangle2D.Double(
                        left, top, Math.ceil(x + scale) - left, Math.ceil(y + scale) - top), ink);
            }
            case ROUNDED -> canvas.fill(
                    new RoundRectangle2D.Double(x, y, scale, scale, scale * 0.6, scale * 0.6), ink);
            case DOTS -> {
                double diameter = scale * 0.86;
                double inset = (scale - diameter) / 2;
                canvas.fill(new Ellipse2D.Double(x + inset, y + inset, diameter, diameter), ink);
            }
            case STAR -> canvas.fill(star(x, y, scale), ink);
            case LIQUID -> paintLiquidModule(canvas, ink, matrix, column, row, dimension, x, y, scale);
        }
    }

    /**
     * A rounded module plus bridges to its right and lower neighbours.
     *
     * <p>Each bridge spans the full height (or width) of the shared edge and reaches a corner
     * radius into both cells. That squares off the shared side, so a run of modules reads as one
     * rounded bar instead of two circles joined by a narrow neck.
     */
    private static void paintLiquidModule(
            Canvas canvas,
            Ink ink,
            BitMatrix matrix,
            int column,
            int row,
            int dimension,
            double x,
            double y,
            double scale) {

        double radius = scale * LIQUID_RADIUS;
        canvas.fill(new RoundRectangle2D.Double(x, y, scale, scale, radius * 2, radius * 2), ink);

        if (isDark(matrix, row, column + 1, dimension)) {
            canvas.fill(new Rectangle2D.Double(x + scale - radius, y, radius * 2, scale), ink);
        }
        if (isDark(matrix, row + 1, column, dimension)) {
            canvas.fill(new Rectangle2D.Double(x, y + scale - radius, scale, radius * 2), ink);
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

    private static void paintEye(Canvas canvas, double x, double y, double scale, RenderOptions options) {
        double side = FINDER * scale;
        Shape outer;
        Shape gap;
        Shape centre;
        switch (options.finderShape()) {
            case SQUARE -> {
                outer = new Rectangle2D.Double(x, y, side, side);
                gap = new Rectangle2D.Double(x + scale, y + scale, side - 2 * scale, side - 2 * scale);
                centre = new Rectangle2D.Double(x + 2 * scale, y + 2 * scale, 3 * scale, 3 * scale);
            }
            case ROUNDED -> {
                outer = rounded(x, y, side, 2.4 * scale);
                gap = rounded(x + scale, y + scale, side - 2 * scale, 1.6 * scale);
                centre = rounded(x + 2 * scale, y + 2 * scale, 3 * scale, 0.9 * scale);
            }
            case CIRCLE -> {
                outer = new Ellipse2D.Double(x, y, side, side);
                gap = new Ellipse2D.Double(x + scale, y + scale, side - 2 * scale, side - 2 * scale);
                centre = new Ellipse2D.Double(x + 2 * scale, y + 2 * scale, 3 * scale, 3 * scale);
            }
            case LEAF -> {
                outer = leaf(x, y, side, 2.4 * scale);
                gap = leaf(x + scale, y + scale, side - 2 * scale, 1.6 * scale);
                centre = leaf(x + 2 * scale, y + 2 * scale, 3 * scale, 0.9 * scale);
            }
            case DIAMOND -> {
                outer = diamond(x, y, side, 0);
                gap = diamond(x, y, side, scale);
                centre = diamond(x, y, side, 2 * scale);
            }
            case CUSTOM -> {
                paintCustomEye(canvas, x, y, scale, options);
                return;
            }
            default -> throw new IllegalStateException("Unknown finder shape");
        }

        Ink ink = finderInk(options);
        if (options.transparentBackground()) {
            canvas.fillRing(outer, gap, ink);
        } else {
            canvas.fill(outer, ink);
            canvas.fill(gap, Ink.of(options.background()));
        }
        canvas.fill(centre, ink);
    }

    /**
     * The standard square eye with the artwork filling its two dark regions.
     *
     * <p>The outer square and the centre are filled with the artwork, then the one-module gap is
     * painted back in the background colour, or left unpainted when the background is
     * transparent. The ring and centre are always present, so the 1:1:3:1:1 structure survives
     * whatever the image contains. The artwork was already flattened over {@code finderColor}, so
     * transparent pixels are that colour.
     */
    private static void paintCustomEye(
            Canvas canvas, double x, double y, double scale, RenderOptions options) {

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

        BufferedImage image = artwork.image();
        double fit = Math.min(side / image.getWidth(), side / image.getHeight());
        int width = (int) Math.round(image.getWidth() * fit);
        int height = (int) Math.round(image.getHeight() * fit);
        int left = (int) Math.round(x + (side - width) / 2.0);
        int top = (int) Math.round(y + (side - height) / 2.0);

        if (options.transparentBackground()) {
            canvas.drawImageInRing(image, outer, gap, left, top, width, height);
        } else {
            canvas.drawImage(image, outer, left, top, width, height);
            canvas.fill(gap, Ink.of(options.background()));
        }
        canvas.drawImage(image, centre, left, top, width, height);
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

    private static void paintLogo(Canvas canvas, RenderOptions options) {
        LogoImage logo = options.logo();
        if (logo == null) {
            return;
        }

        double side = options.size() * logo.sizePercent() / 100.0;
        double padding = side * logo.paddingPercent() / 100.0;
        double plate = side + padding * 2;
        double left = (options.size() - plate) / 2;
        double radius = plate * logo.radiusPercent() / 100.0;

        canvas.fill(new RoundRectangle2D.Double(left, left, plate, plate, radius * 2, radius * 2),
                Ink.of(logo.background()));

        BufferedImage image = logo.image();
        double fit = Math.min(side / image.getWidth(), side / image.getHeight());
        int width = (int) Math.round(image.getWidth() * fit);
        int height = (int) Math.round(image.getHeight() * fit);
        canvas.drawImage(image,
                (options.size() - width) / 2,
                (options.size() - height) / 2,
                width,
                height);
    }
}

package org.skrall.qrcode.render;

import com.google.zxing.common.BitMatrix;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

            graphics.setColor(options.foreground());
            for (int row = 0; row < dimension; row++) {
                for (int column = 0; column < dimension; column++) {
                    if (matrix.get(column, row) && !isFinder(row, column, dimension)) {
                        drawModule(graphics, column, row, scale, options);
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

    private static double offset(int module, RenderOptions options, double scale) {
        return (module + options.margin()) * scale;
    }

    private static void drawModule(Graphics2D graphics, int column, int row, double scale, RenderOptions options) {
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
        Color foreground = options.finderColor();
        Color background = options.background();
        switch (options.finderShape()) {
            case SQUARE -> {
                graphics.setColor(foreground);
                graphics.fill(new Rectangle2D.Double(x, y, side, side));
                graphics.setColor(background);
                graphics.fill(new Rectangle2D.Double(x + scale, y + scale, side - 2 * scale, side - 2 * scale));
                graphics.setColor(foreground);
                graphics.fill(new Rectangle2D.Double(x + 2 * scale, y + 2 * scale, 3 * scale, 3 * scale));
            }
            case ROUNDED -> {
                graphics.setColor(foreground);
                graphics.fill(rounded(x, y, side, 2.4 * scale));
                graphics.setColor(background);
                graphics.fill(rounded(x + scale, y + scale, side - 2 * scale, 1.6 * scale));
                graphics.setColor(foreground);
                graphics.fill(rounded(x + 2 * scale, y + 2 * scale, 3 * scale, 0.9 * scale));
            }
            case CIRCLE -> {
                graphics.setColor(foreground);
                graphics.fill(new Ellipse2D.Double(x, y, side, side));
                graphics.setColor(background);
                graphics.fill(new Ellipse2D.Double(x + scale, y + scale, side - 2 * scale, side - 2 * scale));
                graphics.setColor(foreground);
                graphics.fill(new Ellipse2D.Double(x + 2 * scale, y + 2 * scale, 3 * scale, 3 * scale));
            }
            case LEAF -> {
                graphics.setColor(foreground);
                graphics.fill(leaf(x, y, side, 2.4 * scale));
                graphics.setColor(background);
                graphics.fill(leaf(x + scale, y + scale, side - 2 * scale, 1.6 * scale));
                graphics.setColor(foreground);
                graphics.fill(leaf(x + 2 * scale, y + 2 * scale, 3 * scale, 0.9 * scale));
            }
            case DIAMOND -> {
                graphics.setColor(foreground);
                graphics.fill(diamond(x, y, side, 0));
                graphics.setColor(background);
                graphics.fill(diamond(x, y, side, scale));
                graphics.setColor(foreground);
                graphics.fill(diamond(x, y, side, 2 * scale));
            }
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

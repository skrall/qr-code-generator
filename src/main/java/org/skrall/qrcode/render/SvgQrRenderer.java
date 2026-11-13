package org.skrall.qrcode.render;

import com.google.zxing.common.BitMatrix;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import org.skrall.qrcode.model.FinderShape;
import org.springframework.stereotype.Component;

/**
 * Renders a QR matrix to an SVG document.
 *
 * <p>SVG is offered because it stays sharp at any size and because a logo can be embedded as the
 * original data URL rather than being resampled. The geometry mirrors
 * {@link PngQrRenderer} exactly; the two are kept side by side so a change to one is hard to make
 * without noticing the other.
 */
@Component
public class SvgQrRenderer {

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
        int size = options.size();
        double scale = (double) size / (dimension + options.margin() * 2);

        StringBuilder svg = new StringBuilder(8192);
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(size)
                .append("\" height=\"").append(size)
                .append("\" viewBox=\"0 0 ").append(size).append(' ').append(size)
                .append("\" role=\"img\">\n");
        svg.append("  <title>QR code</title>\n");
        if (options.finderShape() == FinderShape.CUSTOM && options.finderArtwork() != null) {
            appendFinderClipPaths(svg, scale);
        }
        svg.append("  <rect width=\"").append(size).append("\" height=\"").append(size)
                .append("\" fill=\"").append(RenderOptions.hex(options.background())).append("\"/>\n");
        if (options.backgroundImage() != null) {
            svg.append("  <image x=\"0\" y=\"0\" width=\"").append(size)
                    .append("\" height=\"").append(size)
                    .append("\" preserveAspectRatio=\"xMidYMid slice\" href=\"")
                    .append(attribute(options.backgroundImage().dataUrl())).append("\"/>\n");
        }

        svg.append("  <g fill=\"").append(RenderOptions.hex(options.foreground())).append("\">\n");
        for (int row = 0; row < dimension; row++) {
            for (int column = 0; column < dimension; column++) {
                if (matrix.get(column, row) && !isFinder(row, column, dimension)) {
                    appendModule(svg, matrix, column, row, dimension, scale, options);
                }
            }
        }
        svg.append("  </g>\n");

        appendEye(svg, offset(0, options, scale), offset(0, options, scale), scale, options);
        appendEye(svg, offset(dimension - FINDER, options, scale), offset(0, options, scale), scale, options);
        appendEye(svg, offset(0, options, scale), offset(dimension - FINDER, options, scale), scale, options);

        appendLogo(svg, options);
        svg.append("</svg>\n");
        return svg.toString().getBytes(StandardCharsets.UTF_8);
    }

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

    private static double offset(int module, RenderOptions options, double scale) {
        return (module + options.margin()) * scale;
    }

    private static void appendModule(
            StringBuilder svg,
            BitMatrix matrix,
            int column,
            int row,
            int dimension,
            double scale,
            RenderOptions options) {

        double x = (column + options.margin()) * scale;
        double y = (row + options.margin()) * scale;
        switch (options.moduleShape()) {
            case SQUARE -> appendModuleRect(svg, x, y, scale, scale, 0);
            case ROUNDED -> appendModuleRect(svg, x, y, scale, scale, scale * 0.3);
            case DOTS -> {
                double diameter = scale * 0.86;
                double radius = diameter / 2;
                double inset = (scale - diameter) / 2;
                svg.append("    <circle cx=\"").append(num(x + inset + radius))
                        .append("\" cy=\"").append(num(y + inset + radius))
                        .append("\" r=\"").append(num(radius)).append("\"/>\n");
            }
            case STAR -> svg.append("    <path d=\"").append(starPath(x, y, scale)).append("\"/>\n");
            case LIQUID -> appendLiquidModule(svg, matrix, column, row, dimension, x, y, scale);
        }
    }

    /**
     * A rounded module plus bridges to its right and lower neighbours.
     *
     * <p>Each bridge spans the full height (or width) of the shared edge and reaches a corner
     * radius into both cells. That squares off the shared side, so a run of modules reads as one
     * rounded bar instead of two circles joined by a narrow neck.
     */
    private static void appendLiquidModule(
            StringBuilder svg,
            BitMatrix matrix,
            int column,
            int row,
            int dimension,
            double x,
            double y,
            double scale) {

        double radius = scale * LIQUID_RADIUS;
        appendModuleRect(svg, x, y, scale, scale, radius);

        if (isDark(matrix, row, column + 1, dimension)) {
            appendModuleRect(svg, x + scale - radius, y, radius * 2, scale, 0);
        }
        if (isDark(matrix, row + 1, column, dimension)) {
            appendModuleRect(svg, x, y + scale - radius, scale, radius * 2, 0);
        }
    }

    /** A module rect inside the foreground group, so it inherits the group's fill. */
    private static void appendModuleRect(
            StringBuilder svg, double x, double y, double width, double height, double radius) {

        svg.append("    <rect x=\"").append(num(x)).append("\" y=\"").append(num(y))
                .append("\" width=\"").append(num(width)).append("\" height=\"").append(num(height))
                .append('"');
        if (radius > 0) {
            svg.append(" rx=\"").append(num(radius)).append('"');
        }
        svg.append("/>\n");
    }

    /** Mirrors {@link PngQrRenderer}'s star: wide waist, tips at the middle of each cell edge. */
    private static String starPath(double x, double y, double scale) {
        double centreX = x + scale / 2;
        double centreY = y + scale / 2;
        double outer = scale / 2;
        double inner = scale * 0.36 / Math.sqrt(2);
        return "M " + num(centreX) + " " + num(centreY - outer)
                + " L " + num(centreX + inner) + " " + num(centreY - inner)
                + " L " + num(centreX + outer) + " " + num(centreY)
                + " L " + num(centreX + inner) + " " + num(centreY + inner)
                + " L " + num(centreX) + " " + num(centreY + outer)
                + " L " + num(centreX - inner) + " " + num(centreY + inner)
                + " L " + num(centreX - outer) + " " + num(centreY)
                + " L " + num(centreX - inner) + " " + num(centreY - inner)
                + " Z";
    }

    private static void appendEye(StringBuilder svg, double x, double y, double scale, RenderOptions options) {
        double side = FINDER * scale;
        String foreground = RenderOptions.hex(options.finderColor());
        String background = RenderOptions.hex(options.background());
        switch (options.finderShape()) {
            case SQUARE -> {
                appendRect(svg, x, y, side, 0, foreground);
                appendRect(svg, x + scale, y + scale, side - 2 * scale, 0, background);
                appendRect(svg, x + 2 * scale, y + 2 * scale, 3 * scale, 0, foreground);
            }
            case ROUNDED -> {
                appendRect(svg, x, y, side, 2.4 * scale, foreground);
                appendRect(svg, x + scale, y + scale, side - 2 * scale, 1.6 * scale, background);
                appendRect(svg, x + 2 * scale, y + 2 * scale, 3 * scale, 0.9 * scale, foreground);
            }
            case CIRCLE -> {
                appendCircle(svg, x + side / 2, y + side / 2, side / 2, foreground);
                appendCircle(svg, x + side / 2, y + side / 2, side / 2 - scale, background);
                appendCircle(svg, x + side / 2, y + side / 2, 1.5 * scale, foreground);
            }
            case LEAF -> {
                appendPath(svg, leafPath(x, y, side, 2.4 * scale), foreground);
                appendPath(svg, leafPath(x + scale, y + scale, side - 2 * scale, 1.6 * scale), background);
                appendPath(svg, leafPath(x + 2 * scale, y + 2 * scale, 3 * scale, 0.9 * scale), foreground);
            }
            case DIAMOND -> {
                appendPath(svg, diamondPath(x, y, side, 0), foreground);
                appendPath(svg, diamondPath(x, y, side, scale), background);
                appendPath(svg, diamondPath(x, y, side, 2 * scale), foreground);
            }
            case CUSTOM -> appendCustomEye(svg, x, y, scale, options);
        }
    }

    /**
     * Clip paths for a custom eye, in the local coordinates of its translated group.
     *
     * <p>Defined once per document: every eye uses the same seven-module geometry, and the group
     * transform moves the clip paths with it.
     */
    private static void appendFinderClipPaths(StringBuilder svg, double scale) {
        double side = FINDER * scale;
        svg.append("  <defs>\n");
        svg.append("    <clipPath id=\"finder-outer\"><rect x=\"0\" y=\"0\" width=\"")
                .append(num(side)).append("\" height=\"").append(num(side))
                .append("\"/></clipPath>\n");
        svg.append("    <clipPath id=\"finder-centre\"><rect x=\"").append(num(2 * scale))
                .append("\" y=\"").append(num(2 * scale))
                .append("\" width=\"").append(num(3 * scale))
                .append("\" height=\"").append(num(3 * scale))
                .append("\"/></clipPath>\n");
        svg.append("  </defs>\n");
    }

    /**
     * The standard square eye with the artwork filling its two dark regions.
     *
     * <p>Mirrors {@link PngQrRenderer}: the outer square and the centre are filled with the
     * artwork, then the one-module gap is painted back in the background colour. The ring and
     * centre are always present, so the 1:1:3:1:1 structure survives whatever the image contains.
     * The artwork was already flattened over {@code finderColor}, so transparent pixels are that
     * colour.
     */
    private static void appendCustomEye(
            StringBuilder svg, double x, double y, double scale, RenderOptions options) {

        FinderArtworkImage artwork = options.finderArtwork();
        if (artwork == null) {
            // RenderOptions already falls back to SQUARE; this only guards a direct caller.
            return;
        }

        double side = FINDER * scale;
        svg.append("  <g transform=\"translate(").append(num(x)).append(' ').append(num(y))
                .append(")\">\n");
        appendArtworkImage(svg, artwork, side, "finder-outer");
        appendRect(svg, scale, scale, side - 2 * scale, 0, RenderOptions.hex(options.background()));
        appendArtworkImage(svg, artwork, side, "finder-centre");
        svg.append("  </g>\n");
    }

    /** The adjusted artwork, embedded as a PNG so both formats show the same pixels. */
    private static void appendArtworkImage(
            StringBuilder svg, FinderArtworkImage artwork, double side, String clipId) {

        svg.append("    <image x=\"0\" y=\"0\" width=\"").append(num(side))
                .append("\" height=\"").append(num(side))
                .append("\" preserveAspectRatio=\"xMidYMid meet\" clip-path=\"url(#")
                .append(clipId).append(")\" href=\"")
                .append(attribute(artwork.dataUrl())).append("\"/>\n");
    }

    /** Mirrors {@link PngQrRenderer}'s leaf: rounded top-left and bottom-right corners. */
    private static String leafPath(double x, double y, double side, double radius) {
        double r = Math.min(radius, side / 2);
        return "M " + num(x + r) + " " + num(y)
                + " L " + num(x + side) + " " + num(y)
                + " L " + num(x + side) + " " + num(y + side - r)
                + " Q " + num(x + side) + " " + num(y + side) + " " + num(x + side - r) + " " + num(y + side)
                + " L " + num(x) + " " + num(y + side)
                + " L " + num(x) + " " + num(y + r)
                + " Q " + num(x) + " " + num(y) + " " + num(x + r) + " " + num(y)
                + " Z";
    }

    /** Mirrors {@link PngQrRenderer}'s diamond: one bounding box, inset rings stay concentric. */
    private static String diamondPath(double x, double y, double side, double inset) {
        double centreX = x + side / 2;
        double centreY = y + side / 2;
        return "M " + num(centreX) + " " + num(y + inset)
                + " L " + num(x + side - inset) + " " + num(centreY)
                + " L " + num(centreX) + " " + num(y + side - inset)
                + " L " + num(x + inset) + " " + num(centreY)
                + " Z";
    }

    private static void appendPath(StringBuilder svg, String path, String fill) {
        svg.append("  <path d=\"").append(path).append("\" fill=\"").append(fill).append("\"/>\n");
    }

    private static void appendRect(StringBuilder svg, double x, double y, double side, double radius, String fill) {
        svg.append("  <rect x=\"").append(num(x)).append("\" y=\"").append(num(y))
                .append("\" width=\"").append(num(side)).append("\" height=\"").append(num(side))
                .append("\" fill=\"").append(fill).append('"');
        if (radius > 0) {
            svg.append(" rx=\"").append(num(radius)).append('"');
        }
        svg.append("/>\n");
    }

    private static void appendCircle(StringBuilder svg, double cx, double cy, double radius, String fill) {
        svg.append("  <circle cx=\"").append(num(cx)).append("\" cy=\"").append(num(cy))
                .append("\" r=\"").append(num(radius)).append("\" fill=\"").append(fill).append("\"/>\n");
    }

    private static void appendLogo(StringBuilder svg, RenderOptions options) {
        LogoImage logo = options.logo();
        if (logo == null) {
            return;
        }

        double size = options.size();
        double side = size * logo.sizePercent() / 100.0;
        double padding = side * logo.paddingPercent() / 100.0;
        double plate = side + padding * 2;
        double left = (size - plate) / 2;
        double radius = plate * logo.radiusPercent() / 100.0;

        appendRect(svg, left, left, plate, radius, RenderOptions.hex(logo.background()));
        svg.append("  <image x=\"").append(num(left + padding)).append("\" y=\"").append(num(left + padding))
                .append("\" width=\"").append(num(side)).append("\" height=\"").append(num(side))
                .append("\" preserveAspectRatio=\"xMidYMid meet\" href=\"")
                .append(attribute(logo.dataUrl())).append("\"/>\n");
    }

    private static String num(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String attribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

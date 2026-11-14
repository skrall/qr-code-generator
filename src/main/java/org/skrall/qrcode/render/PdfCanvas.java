package org.skrall.qrcode.render;

import static org.skrall.qrcode.render.PdfDocument.num;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.skrall.qrcode.model.GradientType;

/**
 * A PDF page's content stream as a {@link Canvas}: every shape becomes path operators, so the
 * PDF is vector output of exactly the geometry the PNG rasterises.
 *
 * <p>Drawing happens in the painter's pixel space with y pointing down. The constructor's matrix
 * maps that onto the page in points.
 *
 * <ul>
 *   <li>A gradient is a shading pattern (axial or radial, two stops, extended at both ends like
 *       Java2D's {@code NO_CYCLE}), set as the fill colour. A pattern lives in the page's default
 *       space rather than the current one, so its matrix is the transform in force where it is
 *       used, which is why there is one pattern per origin.</li>
 *   <li>A ring is the outer and the inner shape in one path, filled with the even-odd rule; an
 *       image in a ring is clipped to that path the same way. Nothing is ever painted in the
 *       hole, so it stays transparent.</li>
 *   <li>An image is an XObject of 8-bit RGB with PNG predictors under Flate, and a soft mask
 *       when it has any transparency. Each image is embedded once however often it is drawn.</li>
 * </ul>
 */
final class PdfCanvas implements Canvas {

    private final PdfDocument document;
    private final StringBuilder content = new StringBuilder(65536);
    /** The pixel space to page matrix: {@code [scale 0 0 -scale originX originY]}. */
    private final double scale;
    private final double originX;
    private final double originY;
    private final int size;

    private int translateX;
    private int translateY;
    private boolean interpolate;
    private String fill = "";

    private final Map<String, Integer> patterns = new LinkedHashMap<>();
    private final Map<PatternKey, String> patternNames = new LinkedHashMap<>();
    private final Map<ImageKey, String> imageNames = new LinkedHashMap<>();
    private final Map<String, Integer> images = new LinkedHashMap<>();

    private record ImageKey(BufferedImage image, boolean interpolate) {
    }

    private record PatternKey(Gradient gradient, int translateX, int translateY) {
    }

    /**
     * @param scale   points per pixel
     * @param originX where the canvas's top-left corner lies on the page, in points
     * @param originY likewise, measured up from the bottom of the page
     * @param size    the code's side in pixels, which a gradient spans
     */
    PdfCanvas(PdfDocument document, double scale, double originX, double originY, int size) {
        this.document = document;
        this.scale = scale;
        this.originX = originX;
        this.originY = originY;
        this.size = size;
        content.append("q\n").append(num(scale)).append(" 0 0 ").append(num(-scale)).append(' ')
                .append(num(originX)).append(' ').append(num(originY)).append(" cm\n");
    }

    @Override
    public void translate(int x, int y) {
        translateX += x;
        translateY += y;
        content.append("1 0 0 1 ").append(x).append(' ').append(y).append(" cm\n");
    }

    @Override
    public void fill(Shape shape, Ink ink) {
        setFill(ink);
        int rule = appendPath(shape);
        content.append(rule == PathIterator.WIND_EVEN_ODD ? "f*\n" : "f\n");
    }

    @Override
    public void fillRing(Shape outer, Shape hole, Ink ink) {
        setFill(ink);
        appendPath(outer);
        appendPath(hole);
        content.append("f*\n");
    }

    @Override
    public void interpolateImages() {
        interpolate = true;
    }

    @Override
    public void drawImage(BufferedImage image, int x, int y, int width, int height) {
        content.append("q\n");
        appendImage(image, interpolate, x, y, width, height);
        content.append("Q\n");
    }

    @Override
    public void drawImage(BufferedImage image, Shape clip, int x, int y, int width, int height) {
        content.append("q\n");
        int rule = appendPath(clip);
        content.append(rule == PathIterator.WIND_EVEN_ODD ? "W* n\n" : "W n\n");
        appendImage(image, true, x, y, width, height);
        content.append("Q\n");
    }

    @Override
    public void drawImageInRing(
            BufferedImage image, Shape outer, Shape hole, int x, int y, int width, int height) {
        content.append("q\n");
        appendPath(outer);
        appendPath(hole);
        content.append("W* n\n");
        appendImage(image, true, x, y, width, height);
        content.append("Q\n");
    }

    /** The finished content stream, its graphics state restored. */
    byte[] content() {
        return (content + "Q\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    /** The {@code /Pattern} and {@code /XObject} resource entries the content refers to. */
    String resources() {
        StringBuilder resources = new StringBuilder();
        if (!patterns.isEmpty()) {
            resources.append("/Pattern <<");
            patterns.forEach((name, number) -> resources.append(" /").append(name).append(' ')
                    .append(number).append(" 0 R"));
            resources.append(" >> ");
        }
        if (!images.isEmpty()) {
            resources.append("/XObject <<");
            images.forEach((name, number) -> resources.append(" /").append(name).append(' ')
                    .append(number).append(" 0 R"));
            resources.append(" >> ");
        }
        return resources.toString();
    }

    private void setFill(Ink ink) {
        String next = ink.gradient() == null
                ? colour(ink.color()) + " rg"
                : "/Pattern cs /" + pattern(ink.gradient()) + " scn";
        if (!next.equals(fill)) {
            content.append(next).append('\n');
            fill = next;
        }
    }

    private static String colour(Color color) {
        return num(color.getRed() / 255.0) + " " + num(color.getGreen() / 255.0) + " "
                + num(color.getBlue() / 255.0);
    }

    /** The pattern for this gradient at the current origin, added on first use. */
    private String pattern(Gradient gradient) {
        PatternKey key = new PatternKey(gradient, translateX, translateY);
        String existing = patternNames.get(key);
        if (existing != null) {
            return existing;
        }
        String coords;
        int type;
        if (gradient.type() == GradientType.RADIAL) {
            double centre = size / 2.0;
            type = 3;
            coords = num(centre) + " " + num(centre) + " 0 " + num(centre) + " " + num(centre) + " "
                    + num(gradient.radius(size));
        } else {
            Gradient.Line line = gradient.line(size);
            type = 2;
            coords = num(line.x1()) + " " + num(line.y1()) + " " + num(line.x2()) + " " + num(line.y2());
        }
        // The current transform: the page matrix followed by the translations so far.
        String matrix = num(scale) + " 0 0 " + num(-scale) + " "
                + num(originX + scale * translateX) + " " + num(originY - scale * translateY);
        int number = document.add("<< /Type /Pattern /PatternType 2 /Matrix [" + matrix + "]"
                + " /Shading << /ShadingType " + type + " /ColorSpace /DeviceRGB"
                + " /Coords [" + coords + "]"
                + " /Function << /FunctionType 2 /Domain [0 1] /C0 [" + colour(gradient.start())
                + "] /C1 [" + colour(gradient.end()) + "] /N 1 >>"
                + " /Extend [true true] >> >>");
        String name = "P" + patterns.size();
        patterns.put(name, number);
        patternNames.put(key, name);
        return name;
    }

    /**
     * Draws the image into the rectangle. Image space has its origin at the bottom-left, so the
     * matrix flips it back into this canvas's y-down space.
     */
    private void appendImage(BufferedImage image, boolean smooth, int x, int y, int width, int height) {
        String name = imageNames.computeIfAbsent(new ImageKey(image, smooth), key -> {
            String next = "Im" + images.size();
            images.put(next, PdfImages.embed(document, image, smooth));
            return next;
        });
        content.append(width).append(" 0 0 ").append(-height).append(' ')
                .append(x).append(' ').append(y + height).append(" cm /").append(name).append(" Do\n");
    }

    /**
     * Writes a shape as path construction operators, turning quadratic segments into cubic ones,
     * which is all PDF has.
     *
     * @return the shape's winding rule
     */
    private int appendPath(Shape shape) {
        double[] c = new double[6];
        double currentX = 0;
        double currentY = 0;
        double startX = 0;
        double startY = 0;
        PathIterator iterator = shape.getPathIterator(null);
        for (; !iterator.isDone(); iterator.next()) {
            switch (iterator.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> {
                    point(c[0], c[1]).append(" m\n");
                    currentX = startX = c[0];
                    currentY = startY = c[1];
                }
                case PathIterator.SEG_LINETO -> {
                    point(c[0], c[1]).append(" l\n");
                    currentX = c[0];
                    currentY = c[1];
                }
                case PathIterator.SEG_QUADTO -> {
                    double x1 = currentX + 2.0 / 3.0 * (c[0] - currentX);
                    double y1 = currentY + 2.0 / 3.0 * (c[1] - currentY);
                    double x2 = c[2] + 2.0 / 3.0 * (c[0] - c[2]);
                    double y2 = c[3] + 2.0 / 3.0 * (c[1] - c[3]);
                    point(x1, y1).append(' ');
                    point(x2, y2).append(' ');
                    point(c[2], c[3]).append(" c\n");
                    currentX = c[2];
                    currentY = c[3];
                }
                case PathIterator.SEG_CUBICTO -> {
                    point(c[0], c[1]).append(' ');
                    point(c[2], c[3]).append(' ');
                    point(c[4], c[5]).append(" c\n");
                    currentX = c[4];
                    currentY = c[5];
                }
                case PathIterator.SEG_CLOSE -> {
                    content.append("h\n");
                    currentX = startX;
                    currentY = startY;
                }
                default -> throw new IllegalStateException("Unknown path segment");
            }
        }
        return iterator.getWindingRule();
    }

    private StringBuilder point(double x, double y) {
        return content.append(num(x)).append(' ').append(num(y));
    }
}

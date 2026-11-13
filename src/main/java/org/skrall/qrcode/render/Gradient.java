package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import org.skrall.qrcode.GradientException;
import org.skrall.qrcode.model.GradientType;
import org.skrall.qrcode.model.QrGradient;

/**
 * A validated gradient and the geometry both renderers draw it with.
 *
 * <p>The geometry lives here, not in the renderers, so the PNG {@link Paint} and the SVG
 * gradient element cannot drift apart: both read the same line or circle.
 *
 * <p>The gradient spans the whole canvas in user space, not each module, so the colour flows
 * across the code rather than repeating in every cell.
 *
 * @param type    linear or radial
 * @param start   the colour at the start of the line, or at the centre
 * @param end     the colour at the end of the line, or at the corners
 * @param angle   the CSS-style direction of a linear gradient, in degrees
 * @param finders whether the eyes take the gradient as well as the modules
 */
public record Gradient(GradientType type, Color start, Color end, int angle, boolean finders) {

    private static final int DEFAULT_ANGLE = 180;

    /** A line from (x1, y1) to (x2, y2) in pixels. */
    public record Line(double x1, double y1, double x2, double y2) {
    }

    /**
     * Resolves the request's gradient and checks it against the background.
     *
     * <p>Both renderers interpolate the two stops linearly in sRGB, and luminance is a linear
     * function of the sRGB channels, so every colour along the gradient has a luminance between
     * the two stops'. Checking the stops is therefore enough: if both are on the same side of the
     * background and far enough from it, so is everything in between.
     *
     * @return the gradient, or {@code null} when none was requested
     * @throws GradientException when a stop is too close to the background, or the stops sit on
     *                           opposite sides of it
     */
    public static Gradient from(QrGradient source, Color background) {
        if (source == null) {
            return null;
        }

        Color start = Color.decode(source.start());
        Color end = Color.decode(source.end());
        double backgroundLuminance = Images.luminance(background);
        double startLuminance = Images.luminance(start);
        double endLuminance = Images.luminance(end);

        requireContrast("start", start, startLuminance, background, backgroundLuminance);
        requireContrast("end", end, endLuminance, background, backgroundLuminance);
        if ((startLuminance < backgroundLuminance) != (endLuminance < backgroundLuminance)) {
            throw new GradientException(
                    "style.gradient.start and style.gradient.end must both be darker or both be"
                            + " lighter than the background " + RenderOptions.hex(background)
                            + ". Otherwise the gradient passes through the background colour and"
                            + " erases part of the code.");
        }

        return new Gradient(
                source.type() == null ? GradientType.LINEAR : source.type(),
                start,
                end,
                source.angle() == null ? DEFAULT_ANGLE : source.angle(),
                Boolean.TRUE.equals(source.finders()));
    }

    private static void requireContrast(
            String stop, Color colour, double luminance, Color background, double backgroundLuminance) {

        if (Math.abs(luminance - backgroundLuminance) < Images.MIN_CONTRAST) {
            throw new GradientException(
                    "style.gradient." + stop + " " + RenderOptions.hex(colour)
                            + " is too close to the background " + RenderOptions.hex(background)
                            + ": the modules it colours would not scan. Pick a colour whose"
                            + " luminance is at least " + Images.MIN_CONTRAST
                            + "/255 away from the background.");
        }
    }

    /**
     * The gradient line of a linear gradient on a square canvas of the given side.
     *
     * <p>As in CSS, the line runs through the centre at {@link #angle()} and is just long enough
     * that the corners furthest along it get the pure start and end colours.
     */
    public Line line(int size) {
        double radians = Math.toRadians(angle);
        double dx = Math.sin(radians);
        double dy = -Math.cos(radians);
        double half = (Math.abs(dx) + Math.abs(dy)) * size / 2.0;
        double centre = size / 2.0;
        return new Line(centre - dx * half, centre - dy * half, centre + dx * half, centre + dy * half);
    }

    /** The radius of a radial gradient: from the centre to a corner. */
    public double radius(int size) {
        return size / Math.sqrt(2);
    }

    /** The Java2D paint for a canvas of the given side. */
    public Paint paint(int size) {
        float[] fractions = {0f, 1f};
        Color[] colours = {start, end};
        if (type == GradientType.RADIAL) {
            double centre = size / 2.0;
            return new RadialGradientPaint(
                    new Point2D.Double(centre, centre), (float) radius(size), fractions, colours);
        }
        Line line = line(size);
        return new LinearGradientPaint(
                new Point2D.Double(line.x1(), line.y1()),
                new Point2D.Double(line.x2(), line.y2()),
                fractions,
                colours);
    }
}

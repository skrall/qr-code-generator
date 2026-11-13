package org.skrall.qrcode.render;

import java.awt.Color;
import java.util.DoubleSummaryStatistics;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.FinderShape;
import org.skrall.qrcode.model.ModuleShape;
import org.skrall.qrcode.model.QrStyle;

/**
 * A {@link QrStyle} with every default applied and colours parsed.
 *
 * <p>The renderers take this rather than the request DTO so that neither has to know what an
 * absent field means.
 *
 * @param size            the output image's side in pixels
 * @param margin          the quiet zone in modules
 * @param errorCorrection the redundancy level
 * @param foreground      the data modules' colour when there is no gradient
 * @param gradient        the modules' gradient, and the eyes' when it says so, or {@code null}
 * @param background      the background and the eyes' inner gap
 * @param backgroundImage the decoded image behind the code, or {@code null} for none
 * @param moduleShape     the shape of a data module
 * @param finderShape     the shape of the three corner patterns
 * @param finderColor     the corner patterns' colour
 * @param logo            the decoded logo, or {@code null} for none
 * @param finderArtwork   the decoded artwork that fills a {@link FinderShape#CUSTOM} eye
 */
public record RenderOptions(
        int size,
        int margin,
        ErrorCorrection errorCorrection,
        Color foreground,
        Gradient gradient,
        Color background,
        BackgroundImage backgroundImage,
        ModuleShape moduleShape,
        FinderShape finderShape,
        Color finderColor,
        LogoImage logo,
        FinderArtworkImage finderArtwork) {

    public static final int DEFAULT_SIZE = 512;
    public static final int DEFAULT_MARGIN = 4;

    public static RenderOptions from(QrStyle style, LogoImage logo) {
        QrStyle source = style == null ? QrStyle.EMPTY : style;
        int size = clamp(source.size(), 128, 4096, DEFAULT_SIZE);
        Color foreground = parse(source.foreground(), Color.BLACK);
        Color background = parse(source.background(), Color.WHITE);
        Color finderColor = parse(source.finderColor(), foreground);
        Gradient gradient = Gradient.from(source.gradient(), background);
        FinderShape finderShape =
                source.finderShape() == null ? FinderShape.SQUARE : source.finderShape();
        FinderArtworkImage finderArtwork = finderShape == FinderShape.CUSTOM
                ? FinderArtworkImage.decode(source.finderArtwork(), finderColor, background)
                : null;
        if (finderShape == FinderShape.CUSTOM && finderArtwork == null) {
            finderShape = FinderShape.SQUARE;
        }

        // Everything drawn over the image: the modules, the eyes, and the artwork's pixels. A
        // gradient replaces the foreground; its two stops bound every colour along it.
        DoubleSummaryStatistics inks = finderArtwork == null
                ? new DoubleSummaryStatistics()
                : Images.luminances(finderArtwork.image());
        Color moduleInk = gradient == null ? foreground : gradient.start();
        inks.accept(Images.luminance(moduleInk));
        if (gradient != null) {
            inks.accept(Images.luminance(gradient.end()));
        }
        if (gradient == null || !gradient.finders() || finderArtwork != null) {
            inks.accept(Images.luminance(finderColor));
        }
        BackgroundImage backgroundImage = BackgroundImage.decode(
                source.backgroundImage(), moduleInk, background, inks, size);

        return new RenderOptions(
                size,
                clamp(source.margin(), 0, 16, DEFAULT_MARGIN),
                source.errorCorrection() == null ? ErrorCorrection.MEDIUM : source.errorCorrection(),
                foreground,
                gradient,
                background,
                backgroundImage,
                source.moduleShape() == null ? ModuleShape.SQUARE : source.moduleShape(),
                finderShape,
                finderColor,
                logo,
                finderArtwork);
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static Color parse(String hex, Color fallback) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') {
            return fallback;
        }
        try {
            return new Color(Integer.parseInt(hex.substring(1), 16));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Whether the eyes are filled with the gradient rather than {@link #finderColor()}. */
    public boolean gradientFinders() {
        return gradient != null && gradient.finders() && finderShape != FinderShape.CUSTOM;
    }

    /** {@code #rrggbb}, which is also the form the SVG renderer writes. */
    public static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}

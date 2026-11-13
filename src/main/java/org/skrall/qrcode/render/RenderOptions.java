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
 * @param foreground      the data modules' colour
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
        FinderShape finderShape =
                source.finderShape() == null ? FinderShape.SQUARE : source.finderShape();
        FinderArtworkImage finderArtwork = finderShape == FinderShape.CUSTOM
                ? FinderArtworkImage.decode(source.finderArtwork(), finderColor, background)
                : null;
        if (finderShape == FinderShape.CUSTOM && finderArtwork == null) {
            finderShape = FinderShape.SQUARE;
        }

        // Everything dark drawn over the image: the modules, the eyes, and the artwork's pixels.
        DoubleSummaryStatistics inks = finderArtwork == null
                ? new DoubleSummaryStatistics()
                : Images.luminances(finderArtwork.image());
        inks.accept(Images.luminance(foreground));
        inks.accept(Images.luminance(finderColor));
        BackgroundImage backgroundImage = BackgroundImage.decode(
                source.backgroundImage(), foreground, background, inks, size);

        return new RenderOptions(
                size,
                clamp(source.margin(), 0, 16, DEFAULT_MARGIN),
                source.errorCorrection() == null ? ErrorCorrection.MEDIUM : source.errorCorrection(),
                foreground,
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

    /** {@code #rrggbb}, which is also the form the SVG renderer writes. */
    public static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}

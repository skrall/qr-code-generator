package org.skrall.qrcode.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import org.skrall.qrcode.FrameException;
import org.skrall.qrcode.model.QrFrame;

/**
 * The laid-out frame around a code: the canvas, where the code goes, and the shapes to fill.
 *
 * <p>Everything is decided here, once, and both renderers draw the result. The text in particular
 * arrives as glyph outlines from the bundled font, so the PNG and the SVG fill the same shapes
 * rather than each setting the text in whatever font it can find.
 *
 * <p>Every length is rounded to whole pixels. The code therefore lands on the same pixel grid as
 * an unframed one, and the PNG's square modules stay crisp.
 *
 * @param width             the canvas width, bleed included
 * @param height            the canvas height, bleed included
 * @param codeX             the left edge of the code, quiet zone included
 * @param codeY             the top edge of the code, quiet zone included
 * @param background        the card colour, which also fills the bleed; with a transparent
 *                          background the card is not painted and this is the code's background,
 *                          the surface the card is meant to sit on
 * @param border            the border ring, or {@code null} for none
 * @param borderColor       the border colour
 * @param text              the title and caption outlines, or {@code null} for no text
 * @param textColor         the title and caption colour
 * @param clearance         pixels between the code and the nearest border or text
 * @param backgroundIsQuiet whether the card contrasts with the modules enough to count as quiet
 *                          zone
 * @param title             the title as given, for the SVG's accessible name
 * @param caption           the caption as given, for the SVG's description
 */
public record Frame(
        int width,
        int height,
        int codeX,
        int codeY,
        Color background,
        Shape border,
        Color borderColor,
        Shape text,
        Color textColor,
        int clearance,
        boolean backgroundIsQuiet,
        String title,
        String caption) {

    /**
     * The least clear space, in modules, a frame may leave around the code.
     *
     * <p>The QR specification asks for four; this is a floor, not a recommendation. With a heavy
     * border against the code, ZXing fails to decode a clean render at zero modules and reads it
     * at one. A camera adds blur and perspective that a clean render does not, so the floor keeps
     * one module of headroom over that.
     */
    static final int MIN_QUIET_MODULES = 2;

    /** The framed canvas may not be larger than this on either side. */
    static final int MAX_SIDE = 6144;

    /** Nor larger than this in area: half as much again as the largest unframed code. */
    static final long MAX_AREA = 4096L * 4096L * 3 / 2;

    private static final int DEFAULT_TITLE_SIZE = 9;
    private static final int DEFAULT_CAPTION_SIZE = 5;
    private static final int DEFAULT_BORDER = 2;
    private static final int DEFAULT_RADIUS = 4;
    private static final int DEFAULT_PADDING = 6;

    /** Line height as a multiple of the font size. */
    private static final double LINE_HEIGHT = 1.25;

    /** The gap between a line of text and the code, as a multiple of that text's font size. */
    private static final double TEXT_GAP = 0.5;

    /**
     * Measures and outlines text independently of any screen: anti-aliased, fractional metrics.
     *
     * <p>The outlines are resolution-free shapes, so the same ones serve the PNG and the SVG.
     */
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    /**
     * Lays out the frame around a code of the given side.
     *
     * @param background the code's background, the default card colour
     * @param moduleInk  the modules' colour, or their gradient's start: the default text colour
     * @param inks       the luminances of everything drawn in the code, to decide whether the
     *                   card is light (or dark) enough to count as quiet zone
     * @param transparent whether the card is left unpainted. Its colour is then ignored: what
     *                   shows through is the surface the code sits on, which is {@code background}
     * @return the frame, or {@code null} when none was requested
     * @throws FrameException when the text needs characters the font lacks, or the canvas would
     *                        be too large
     */
    public static Frame from(
            QrFrame source,
            int size,
            Color background,
            Color moduleInk,
            DoubleSummaryStatistics inks,
            boolean transparent) {

        if (source == null) {
            return null;
        }

        Color card = transparent ? background : RenderOptions.parse(source.background(), background);
        Color textColor = RenderOptions.parse(source.textColor(), moduleInk);
        Color borderColor = RenderOptions.parse(source.borderColor(), textColor);

        int bleed = px(size, source.bleedPercent(), 0);
        int margin = px(size, source.marginPercent(), 0);
        int border = px(size, source.borderPercent(), DEFAULT_BORDER);
        int padding = px(size, source.paddingPercent(), DEFAULT_PADDING);
        double radius = size * percent(source.radiusPercent(), DEFAULT_RADIUS) / 100.0;

        String title = clean(source.title());
        String caption = clean(source.caption());
        TextBlock titleBlock = TextBlock.of(title, FrameFonts.BOLD,
                size * percent(source.titleSizePercent(), DEFAULT_TITLE_SIZE) / 100.0, size,
                "style.frame.title");
        TextBlock captionBlock = TextBlock.of(caption, FrameFonts.REGULAR,
                size * percent(source.captionSizePercent(), DEFAULT_CAPTION_SIZE) / 100.0, size,
                "style.frame.caption");

        int edge = bleed + margin + border + padding;
        int titleGap = titleBlock == null ? 0 : (int) Math.round(titleBlock.fontSize() * TEXT_GAP);
        int captionGap = captionBlock == null ? 0 : (int) Math.round(captionBlock.fontSize() * TEXT_GAP);
        int titleHeight = titleBlock == null ? 0 : titleBlock.height();
        int captionHeight = captionBlock == null ? 0 : captionBlock.height();

        int codeX = edge;
        int codeY = edge + titleHeight + titleGap;
        int width = size + 2 * edge;
        int height = codeY + size + captionGap + captionHeight + edge;
        if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_AREA) {
            throw new FrameException("The framed image would be " + width + " × " + height
                    + " pixels, over the limit of " + MAX_SIDE + " on a side and " + MAX_AREA
                    + " in all. Lower style.size or the frame's sizes.");
        }

        Path2D.Double text = new Path2D.Double(Path2D.WIND_NON_ZERO);
        if (titleBlock != null) {
            titleBlock.appendTo(text, codeX, edge, size);
        }
        if (captionBlock != null) {
            captionBlock.appendTo(text, codeX, codeY + size + captionGap, size);
        }

        // Where a border or text is nearest the code. Without either, nothing dark comes close.
        int sides = border > 0 ? padding : Integer.MAX_VALUE;
        int top = titleBlock != null ? titleGap : sides;
        int bottom = captionBlock != null ? captionGap : sides;

        return new Frame(
                width,
                height,
                codeX,
                codeY,
                card,
                border > 0 ? ring(bleed + margin, width, height, border, radius) : null,
                borderColor,
                titleBlock == null && captionBlock == null ? null : text,
                textColor,
                Math.min(sides, Math.min(top, bottom)),
                isQuiet(card, background, moduleInk, inks),
                title,
                caption);
    }

    /**
     * Rejects a frame that leaves too little clear space around the code.
     *
     * <p>The code's own margin always counts. The frame's padding counts as well when the card
     * contrasts with the modules the way the code's background does; a dark card next to a light
     * code is itself an edge a reader can mistake for modules, so then only the margin counts.
     *
     * @param dimension the code's side in modules, without the quiet zone
     * @param margin    the code's quiet zone in modules
     */
    public void requireQuietZone(int dimension, int size, int margin) {
        double module = (double) size / (dimension + 2 * margin);
        double clear = margin * module + (backgroundIsQuiet ? clearance : 0);
        if (clear + 0.5 < MIN_QUIET_MODULES * module) {
            throw new FrameException(String.format(
                    "The frame leaves %.1f modules of clear space around the code; it needs at least"
                            + " %d, or the border, text or card edge is read as part of the code."
                            + " Raise style.margin or style.frame.paddingPercent%s.",
                    clear / module,
                    MIN_QUIET_MODULES,
                    backgroundIsQuiet ? "" : ", or use a card colour that contrasts with the modules"));
        }
    }

    /**
     * Whether the card contrasts with every ink in the code, on the same side as its background.
     *
     * <p>Then the card reads as more quiet zone; otherwise it is an edge in its own right.
     */
    private static boolean isQuiet(
            Color card, Color background, Color moduleInk, DoubleSummaryStatistics inks) {

        double cardLuminance = Images.luminance(card);
        boolean lightCode = Images.luminance(background) >= Images.luminance(moduleInk);
        return lightCode
                ? cardLuminance >= inks.getMax() + Images.MIN_CONTRAST
                : cardLuminance <= inks.getMin() - Images.MIN_CONTRAST;
    }

    /** A rounded border of the given width, concentric inside and out. */
    private static Shape ring(int inset, int width, int height, int border, double radius) {
        double outerWidth = width - 2.0 * inset;
        double outerHeight = height - 2.0 * inset;
        Area ring = new Area(new RoundRectangle2D.Double(
                inset, inset, outerWidth, outerHeight, radius * 2, radius * 2));
        double inner = Math.max(0, radius - border);
        ring.subtract(new Area(new RoundRectangle2D.Double(
                inset + border, inset + border,
                outerWidth - 2.0 * border, outerHeight - 2.0 * border,
                inner * 2, inner * 2)));
        return ring;
    }

    private static int px(int size, Integer percent, int fallback) {
        return (int) Math.round(size * percent(percent, fallback) / 100.0);
    }

    private static int percent(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    /** Trimmed text, with Windows line endings folded, or {@code null} when there is none. */
    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Lines of one font size, broken to fit the code's width and centred on it.
     *
     * @param lines    each line's layout, or {@code null} for a blank line
     * @param fontSize the font size in pixels
     * @param ascent   the font's ascent at that size
     * @param descent  the font's descent at that size
     */
    private record TextBlock(List<TextLayout> lines, double fontSize, double ascent, double descent) {

        static TextBlock of(String text, Font face, double fontSize, double wrapWidth, String field) {
            if (text == null) {
                return null;
            }
            requireDrawable(text, face, field);

            Font font = face.deriveFont((float) fontSize);
            List<TextLayout> lines = new ArrayList<>();
            for (String paragraph : text.split("\n", -1)) {
                if (paragraph.isBlank()) {
                    lines.add(null);
                    continue;
                }
                AttributedString attributed = new AttributedString(paragraph);
                attributed.addAttribute(TextAttribute.FONT, font);
                LineBreakMeasurer measurer = new LineBreakMeasurer(attributed.getIterator(), FRC);
                while (measurer.getPosition() < paragraph.length()) {
                    lines.add(measurer.nextLayout((float) wrapWidth));
                }
            }

            LineMetrics metrics = font.getLineMetrics("Hg", FRC);
            return new TextBlock(lines, fontSize, metrics.getAscent(), metrics.getDescent());
        }

        /** Rejects text the bundled font would draw as empty boxes. */
        private static void requireDrawable(String text, Font face, String field) {
            String printable = text.replace("\n", "");
            int index = face.canDisplayUpTo(printable);
            if (index >= 0) {
                int codePoint = printable.codePointAt(index);
                throw new FrameException(String.format(
                        "%s contains \"%s\" (U+%04X), which the bundled font cannot draw. It"
                                + " covers Latin, Greek and Cyrillic text; emoji and other scripts"
                                + " are not supported.",
                        field, new String(Character.toChars(codePoint)), codePoint));
            }
        }

        double lineHeight() {
            return fontSize * LINE_HEIGHT;
        }

        int height() {
            return (int) Math.ceil(lines.size() * lineHeight());
        }

        /** Adds each line's outline, centred on a column of the given width. */
        void appendTo(Path2D path, double left, double top, double width) {
            double baseline = top + (lineHeight() - (ascent + descent)) / 2 + ascent;
            for (TextLayout line : lines) {
                if (line != null) {
                    double x = left + (width - line.getVisibleAdvance()) / 2;
                    path.append(line.getOutline(AffineTransform.getTranslateInstance(x, baseline)), false);
                }
                baseline += lineHeight();
            }
        }
    }
}

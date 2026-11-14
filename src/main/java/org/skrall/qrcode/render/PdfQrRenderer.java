package org.skrall.qrcode.render;

import static org.skrall.qrcode.render.PdfDocument.num;

import com.google.zxing.common.BitMatrix;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Renders a QR matrix to a one-page vector PDF.
 *
 * <p>The page is the image at a physical size: {@code dpi} pixels to the inch, so a 600-pixel code
 * at 300 DPI is two inches square. The geometry is {@link QrPainter}'s, the same the PNG
 * rasterises, drawn on a {@link PdfCanvas}. Frame text is filled as glyph outlines, so no font is
 * embedded.
 *
 * <p>Page boxes. Without bleed the MediaBox and the TrimBox are the image. With a frame's bleed,
 * the BleedBox is the image, the TrimBox is the image less the bleed on every side, and the
 * MediaBox adds a slug of {@link #SLUG} points around the bleed for crop marks. The marks sit on
 * the trim lines, extended outwards, starting {@link #MARK_GAP} points beyond the bleed, so no
 * mark reaches into the bleed or the trimmed page. They are drawn in the registration colour
 * ({@code /Separation /All}), which prints on every plate.
 */
@Component
public class PdfQrRenderer {

    /** Points per inch, the PDF unit. */
    static final double POINTS_PER_INCH = 72;

    /** The distance from the bleed edge to the start of a crop mark, in points. */
    static final double MARK_GAP = 3;

    /** A crop mark's length, in points: a quarter of an inch, as most layout software draws it. */
    static final double MARK_LENGTH = 18;

    /** A crop mark's stroke width, in points. */
    static final double MARK_WIDTH = 0.25;

    /** The space around the bleed that holds the crop marks, in points. */
    static final double SLUG = MARK_GAP + MARK_LENGTH + 6;

    public byte[] render(BitMatrix matrix, RenderOptions options, int dpi) {
        double scale = POINTS_PER_INCH / dpi;
        double width = options.width() * scale;
        double height = options.height() * scale;
        Frame frame = options.frame();
        double bleed = frame == null ? 0 : frame.bleed() * scale;
        double slug = bleed > 0 ? SLUG : 0;

        PdfDocument document = new PdfDocument();
        int catalog = document.reserve();
        int pages = document.reserve();

        PdfCanvas canvas = new PdfCanvas(document, scale, slug, slug + height, options.size());
        QrPainter.paint(canvas, matrix, options);

        StringBuilder content = new StringBuilder(
                new String(canvas.content(), StandardCharsets.ISO_8859_1));
        String colourSpaces = "";
        if (bleed > 0) {
            appendCropMarks(content, slug, slug, width, height, bleed);
            colourSpaces = "/ColorSpace << /Registration [/Separation /All /DeviceCMYK"
                    + " << /FunctionType 2 /Domain [0 1] /C0 [0 0 0 0] /C1 [1 1 1 1] /N 1 >>] >> ";
        }
        int contents = document.addFlateStream("",
                content.toString().getBytes(StandardCharsets.ISO_8859_1));

        String boxes = "/MediaBox " + box(0, 0, width + 2 * slug, height + 2 * slug);
        if (bleed > 0) {
            boxes += " /BleedBox " + box(slug, slug, slug + width, slug + height);
        }
        boxes += " /TrimBox " + box(slug + bleed, slug + bleed, slug + width - bleed, slug + height - bleed);

        int page = document.add("<< /Type /Page /Parent " + pages + " 0 R " + boxes
                + " /Resources << " + canvas.resources() + colourSpaces + ">>"
                + " /Contents " + contents + " 0 R >>");
        document.set(pages, "<< /Type /Pages /Kids [" + page + " 0 R] /Count 1 >>");
        document.set(catalog, "<< /Type /Catalog /Pages " + pages + " 0 R >>");

        // The text is outlines, so the title is the one place a reader or a search finds it.
        String title = frame == null || frame.title() == null ? "QR code" : "QR code: " + frame.title();
        int info = document.add("<< /Title " + PdfDocument.text(title)
                + (frame != null && frame.caption() != null
                        ? " /Subject " + PdfDocument.text(frame.caption()) : "")
                + " /Producer (qr-code-generator) >>");
        return document.toBytes(catalog, info);
    }

    /**
     * Eight hairlines, two at each corner, on the trim lines and outside the bleed.
     *
     * @param left   the bleed box's left edge, in points
     * @param bottom the bleed box's bottom edge
     * @param width  the bleed box's width
     * @param height the bleed box's height
     * @param bleed  the bleed, so the trim lines are this far inside the bleed box
     */
    private static void appendCropMarks(
            StringBuilder content, double left, double bottom, double width, double height, double bleed) {

        double right = left + width;
        double top = bottom + height;
        content.append("q\n/Registration CS 1 SCN ").append(num(MARK_WIDTH)).append(" w 0 J\n");
        for (double x : new double[] {left + bleed, right - bleed}) {
            line(content, x, bottom - MARK_GAP, x, bottom - MARK_GAP - MARK_LENGTH);
            line(content, x, top + MARK_GAP, x, top + MARK_GAP + MARK_LENGTH);
        }
        for (double y : new double[] {bottom + bleed, top - bleed}) {
            line(content, left - MARK_GAP, y, left - MARK_GAP - MARK_LENGTH, y);
            line(content, right + MARK_GAP, y, right + MARK_GAP + MARK_LENGTH, y);
        }
        content.append("Q\n");
    }

    private static void line(StringBuilder content, double x1, double y1, double x2, double y2) {
        content.append(num(x1)).append(' ').append(num(y1)).append(" m ")
                .append(num(x2)).append(' ').append(num(y2)).append(" l S\n");
    }

    private static String box(double left, double bottom, double right, double top) {
        return "[" + num(left) + " " + num(bottom) + " " + num(right) + " " + num(top) + "]";
    }
}

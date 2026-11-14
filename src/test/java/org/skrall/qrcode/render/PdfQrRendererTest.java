package org.skrall.qrcode.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.check.ScanChecker;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.service.QrCodeService;

/**
 * The PDF: the same geometry as the PNG, the page boxes and crop marks, vector text and embedded
 * images. PDFs are read back and rasterised with PDFBox.
 */
class PdfQrRendererTest {

    /** At 72 DPI a pixel is a point, so a raster at 72 DPI lines up with the PNG pixel for pixel. */
    private static final int ONE_TO_ONE = 72;

    private final QrCodeService service = new QrCodeService(
            new PngQrRenderer(), new SvgQrRenderer(), new PdfQrRenderer(), new ScanChecker());

    @Test
    void everyStyleRasterisesToThePngAndScans() throws IOException {
        int decoded = 0;
        for (Map.Entry<String, QrCodeRequest> fixture : RenderFixtures.all().entrySet()) {
            String name = fixture.getKey();
            BufferedImage png = ImageIO.read(new ByteArrayInputStream(
                    service.render(fixture.getValue(), QrFormat.PNG)));
            byte[] pdf = service.render(fixture.getValue(), QrFormat.PDF, ONE_TO_ONE);

            try (PDDocument document = Loader.loadPDF(pdf)) {
                PDRectangle bleed = document.getPage(0).getBleedBox();
                assertThat(bleed.getWidth()).as(name).isCloseTo(png.getWidth(), within(0.01f));
                assertThat(bleed.getHeight()).as(name).isCloseTo(png.getHeight(), within(0.01f));
                BufferedImage page = new PDFRenderer(document)
                        .renderImageWithDPI(0, ONE_TO_ONE, ImageType.ARGB);
                // The page may have a slug for crop marks; the image is the bleed box.
                BufferedImage raster = page.getSubimage(
                        Math.round(bleed.getLowerLeftX()),
                        Math.round(page.getHeight() - bleed.getUpperRightY()),
                        png.getWidth(), png.getHeight());

                assertMatches(name, png, raster);
                // Whatever the PNG gives a plain reader, the PDF gives too.
                String fromPdf = decode(flatten(raster));
                assertThat(fromPdf).as(name + " decodes as the PNG does").isEqualTo(decode(flatten(png)));
                if (RenderFixtures.DATA.equals(fromPdf)) {
                    decoded++;
                }
            }
        }
        // A busy background image can defeat a reader without TRY_HARDER's help on an unwashed
        // edge; every other style must read.
        assertThat(decoded).isGreaterThanOrEqualTo(RenderFixtures.all().size() - 2);
    }

    @Test
    void aTransparentBackgroundLeavesTheQuietZoneAndTheEyeGapsUnpainted() throws IOException {
        for (String name : List.of("transparent", "transparent-gradient", "custom-finder-transparent")) {
            QrCodeRequest request = RenderFixtures.all().get(name);
            BufferedImage raster = raster(service.render(request, QrFormat.PDF, ONE_TO_ONE));
            BufferedImage png = ImageIO.read(new ByteArrayInputStream(service.render(request, QrFormat.PNG)));

            assertThat(raster.getRGB(1, 1) >>> 24).as(name + " corner").isZero();
            // The top-left eye: the gap is one module in from the edge of the eye.
            double module = 300.0 / (29 + 8);
            int gapX = (int) Math.round((4 + 3.5) * module);
            int gapY = (int) Math.round((4 + 1.5) * module);
            int ringY = (int) Math.round((4 + 0.5) * module);
            assertThat(png.getRGB(gapX, gapY) >>> 24).as(name + " PNG gap").isZero();
            assertThat(raster.getRGB(gapX, gapY) >>> 24).as(name + " gap").isZero();
            assertThat(raster.getRGB(gapX, ringY) >>> 24).as(name + " ring").isEqualTo(255);
        }
    }

    @Test
    void thePageIsTheImageAtTheResolution() throws IOException {
        // 300 pixels at 300 DPI is an inch: 72 points.
        try (PDDocument document = Loader.loadPDF(service.render(
                RenderFixtures.request("{\"size\":300}"), QrFormat.PDF, 300))) {
            PDPage page = document.getPage(0);
            assertBox(page.getMediaBox(), 0, 0, 72, 72);
            assertBox(page.getTrimBox(), 0, 0, 72, 72);
            assertThat(page.getCOSObject().containsKey(COSName.BLEED_BOX)).isFalse();
            assertThat(contentOf(page)).doesNotContain("/Registration");
        }
        // Without a dpi the page is sized at 300.
        try (PDDocument document = Loader.loadPDF(service.render(
                RenderFixtures.request("{\"size\":600}"), QrFormat.PDF))) {
            assertBox(document.getPage(0).getMediaBox(), 0, 0, 144, 144);
        }
    }

    @Test
    void aFrameWithBleedHasTrimAndBleedBoxesAndCropMarksOutsideThem() throws IOException {
        QrCodeRequest request = RenderFixtures.all().get("frame-bleed");
        BufferedImage png = ImageIO.read(new ByteArrayInputStream(service.render(request, QrFormat.PNG)));
        int dpi = 300;
        double scale = 72.0 / dpi;
        double bleed = Math.round(300 * 0.06) * scale;
        double slug = PdfQrRenderer.SLUG;
        double width = png.getWidth() * scale;
        double height = png.getHeight() * scale;

        try (PDDocument document = Loader.loadPDF(service.render(request, QrFormat.PDF, dpi))) {
            PDPage page = document.getPage(0);
            PDRectangle media = page.getMediaBox();
            PDRectangle bleedBox = page.getBleedBox();
            PDRectangle trim = page.getTrimBox();
            assertBox(media, 0, 0, width + 2 * slug, height + 2 * slug);
            assertBox(bleedBox, slug, slug, slug + width, slug + height);
            assertBox(trim, slug + bleed, slug + bleed, slug + width - bleed, slug + height - bleed);

            List<double[]> marks = cropMarks(contentOf(page));
            assertThat(marks).hasSize(8);
            for (double[] mark : marks) {
                double x1 = mark[0];
                double y1 = mark[1];
                double x2 = mark[2];
                double y2 = mark[3];
                // On a trim line, extended outwards.
                boolean vertical = x1 == x2;
                double offTrim = vertical
                        ? Math.min(Math.abs(x1 - trim.getLowerLeftX()), Math.abs(x1 - trim.getUpperRightX()))
                        : Math.min(Math.abs(y1 - trim.getLowerLeftY()), Math.abs(y1 - trim.getUpperRightY()));
                assertThat(offTrim).as("distance from a trim line").isLessThan(0.01);
                // Wholly outside the bleed box, so never on the trimmed page or its bleed, and on
                // the media.
                for (double[] point : new double[][] {{x1, y1}, {x2, y2}}) {
                    boolean inside = point[0] > bleedBox.getLowerLeftX() && point[0] < bleedBox.getUpperRightX()
                            && point[1] > bleedBox.getLowerLeftY() && point[1] < bleedBox.getUpperRightY();
                    assertThat(inside).as("mark point %s,%s", point[0], point[1]).isFalse();
                    assertThat(point[0]).isBetween(0.0, (double) media.getUpperRightX());
                    assertThat(point[1]).isBetween(0.0, (double) media.getUpperRightY());
                }
                double gap = vertical
                        ? Math.min(Math.abs(Math.max(y1, y2) - bleedBox.getLowerLeftY()),
                                Math.abs(Math.min(y1, y2) - bleedBox.getUpperRightY()))
                        : Math.min(Math.abs(Math.max(x1, x2) - bleedBox.getLowerLeftX()),
                                Math.abs(Math.min(x1, x2) - bleedBox.getUpperRightX()));
                assertThat(gap).isCloseTo(PdfQrRenderer.MARK_GAP, within(0.01));
            }

            // The marks are the only ink in the slug: the corner of the media stays blank, and so
            // does the area just beside the bleed box away from a mark.
            BufferedImage raster = new PDFRenderer(document).renderImageWithDPI(0, 72, ImageType.ARGB);
            assertThat(raster.getRGB(2, 2) >>> 24).isZero();
            int markX = (int) Math.round(trim.getLowerLeftX());
            int markY = (int) Math.round(raster.getHeight() - (bleedBox.getLowerLeftY() - 10));
            assertThat(raster.getRGB(markX, markY) >>> 24).as("on a crop mark").isGreaterThan(0);
        }
    }

    @Test
    void frameTextIsDrawnAsOutlinesWithNoFont() throws IOException {
        try (PDDocument document = Loader.loadPDF(service.render(
                RenderFixtures.all().get("frame-bleed"), QrFormat.PDF, 300))) {
            PDResources resources = document.getPage(0).getResources();
            assertThat(resources.getFontNames()).isEmpty();
            assertThat(new PDFTextStripper().getText(document)).isBlank();
            // The title is still findable, in the document information.
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("QR code: Scan me");
            assertThat(document.getDocumentInformation().getSubject()).isEqualTo("Menu, hours and bookings");
        }
    }

    @Test
    void imagesAreEmbeddedOnceWithTheirTransparency() throws IOException {
        try (PDDocument document = Loader.loadPDF(service.render(
                RenderFixtures.all().get("logo-png"), QrFormat.PDF, 300))) {
            List<PDImageXObject> images = images(document.getPage(0).getResources());
            assertThat(images).hasSize(1);
            assertThat(images.getFirst().getWidth()).isEqualTo(64);
            assertThat(images.getFirst().getCOSObject().containsKey(COSName.SMASK)).isTrue();
        }
        try (PDDocument document = Loader.loadPDF(service.render(
                RenderFixtures.all().get("logo-jpeg-with-background"), QrFormat.PDF, 300))) {
            List<PDImageXObject> images = images(document.getPage(0).getResources());
            // The washed background and the logo; a JPEG has no transparency to mask.
            assertThat(images).hasSize(2);
            assertThat(images).allSatisfy(image ->
                    assertThat(image.getCOSObject().containsKey(COSName.SMASK)).isFalse());
        }
        try (PDDocument document = Loader.loadPDF(service.render(
                RenderFixtures.all().get("custom-finder"), QrFormat.PDF, 300))) {
            // Drawn six times (three eyes, ring and centre), embedded once.
            assertThat(images(document.getPage(0).getResources())).hasSize(1);
        }
    }

    @Test
    void aGradientIsAShadingPattern() throws IOException {
        try (PDDocument document = Loader.loadPDF(service.render(
                RenderFixtures.all().get("linear-gradient"), QrFormat.PDF, 300))) {
            PDResources resources = document.getPage(0).getResources();
            List<COSName> patterns = new ArrayList<>();
            resources.getPatternNames().forEach(patterns::add);
            assertThat(patterns).hasSize(1);
        }
    }

    private static void assertMatches(String name, BufferedImage png, BufferedImage pdf) {
        int differing = 0;
        int alphaDiffering = 0;
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) {
                int a = png.getRGB(x, y);
                int b = pdf.getRGB(x, y);
                if (Math.abs((a >>> 24) - (b >>> 24)) > 96) {
                    alphaDiffering++;
                } else if (Math.abs(luminance(a) - luminance(b)) > 96) {
                    differing++;
                }
            }
        }
        double pixels = png.getWidth() * png.getHeight();
        // Edges are anti-aliased differently and images resampled differently; whole modules
        // or eyes out of place would be far more than this.
        assertThat(differing / pixels).as(name + " pixels that differ in lightness").isLessThan(0.01);
        assertThat(alphaDiffering / pixels).as(name + " pixels that differ in coverage").isLessThan(0.01);
    }

    private static double luminance(int argb) {
        Color colour = new Color(argb, true);
        double alpha = colour.getAlpha() / 255.0;
        // Over white, so a transparent pixel compares as the paper it would print on.
        return (0.299 * colour.getRed() + 0.587 * colour.getGreen() + 0.114 * colour.getBlue()) * alpha
                + 255 * (1 - alpha);
    }

    private static BufferedImage raster(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFRenderer(document).renderImageWithDPI(0, ONE_TO_ONE, ImageType.ARGB);
        }
    }

    private static BufferedImage flatten(BufferedImage image) {
        BufferedImage flat = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flat.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, flat.getWidth(), flat.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return flat;
    }

    private static String decode(BufferedImage image) {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
        try {
            return new MultiFormatReader()
                    .decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE))
                    .getText();
        } catch (NotFoundException e) {
            return null;
        }
    }

    private static void assertBox(PDRectangle box, double left, double bottom, double right, double top) {
        assertThat((double) box.getLowerLeftX()).isCloseTo(left, within(0.01));
        assertThat((double) box.getLowerLeftY()).isCloseTo(bottom, within(0.01));
        assertThat((double) box.getUpperRightX()).isCloseTo(right, within(0.01));
        assertThat((double) box.getUpperRightY()).isCloseTo(top, within(0.01));
    }

    private static String contentOf(PDPage page) throws IOException {
        try (InputStream in = page.getContents()) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    /** The lines stroked in the registration colour: {@code x1 y1 m x2 y2 l S}. */
    private static List<double[]> cropMarks(String content) {
        int start = content.indexOf("/Registration CS");
        assertThat(start).as("crop marks are drawn").isNotNegative();
        Matcher matcher = Pattern.compile("(-?[\\d.]+) (-?[\\d.]+) m (-?[\\d.]+) (-?[\\d.]+) l S")
                .matcher(content.substring(start));
        List<double[]> marks = new ArrayList<>();
        while (matcher.find()) {
            marks.add(new double[] {
                Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)),
                Double.parseDouble(matcher.group(3)), Double.parseDouble(matcher.group(4))});
        }
        return marks;
    }

    private static List<PDImageXObject> images(PDResources resources) throws IOException {
        List<PDImageXObject> images = new ArrayList<>();
        for (COSName name : resources.getXObjectNames()) {
            if (resources.getXObject(name) instanceof PDImageXObject image) {
                images.add(image);
            }
        }
        return images;
    }
}

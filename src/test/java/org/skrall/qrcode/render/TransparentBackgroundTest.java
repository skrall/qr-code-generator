package org.skrall.qrcode.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.BackgroundImageException;
import org.skrall.qrcode.check.ScanChecker;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.FinderShape;
import org.skrall.qrcode.model.ModuleShape;
import org.skrall.qrcode.model.QrBackgroundImage;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFinderArtwork;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.model.QrFrame;
import org.skrall.qrcode.model.QrLogo;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.service.QrCodeService;

/**
 * A transparent background: nothing but ink is painted, in both formats.
 *
 * <p>The codes here are version 1 (21 modules) at a 4-module margin and 290 pixels, so every
 * module is exactly 10 pixels and a module's centre is a whole pixel.
 */
class TransparentBackgroundTest {

    private static final String DATA = "hi";
    private static final int SIZE = 290;
    private static final int MARGIN = 4;
    private static final int MODULE = 10;

    private final QrCodeService service =
            new QrCodeService(new PngQrRenderer(), new SvgQrRenderer(), new ScanChecker());

    @Test
    void leavesTheQuietZoneAndLightModulesTransparentAndDarkModulesOpaque() throws IOException {
        for (FinderShape finderShape : new FinderShape[] {
                FinderShape.SQUARE, FinderShape.ROUNDED, FinderShape.CIRCLE,
                FinderShape.LEAF, FinderShape.DIAMOND}) {
            BufferedImage image = png(style(ModuleShape.SQUARE, finderShape, null, null, null, true));
            BitMatrix matrix = matrix();

            assertThat(alpha(image, 0, 0)).as("corner of the quiet zone").isZero();
            assertThat(alpha(image, MARGIN * MODULE / 2, SIZE / 2)).as("side of the quiet zone").isZero();
            for (int row = 0; row < matrix.getHeight(); row++) {
                for (int column = 0; column < matrix.getWidth(); column++) {
                    if (inFinder(row, column, matrix.getWidth())) {
                        continue;
                    }
                    int alpha = alpha(image, centre(column), centre(row));
                    if (matrix.get(column, row)) {
                        assertThat(alpha).as("dark module %d,%d", column, row).isEqualTo(255);
                    } else {
                        assertThat(alpha).as("light module %d,%d", column, row).isZero();
                    }
                }
            }
            // Every eye: the outer ring and the centre are ink, the one-module gap is not.
            for (int[] eye : new int[][] {{0, 0}, {14, 0}, {0, 14}}) {
                assertThat(alpha(image, centre(eye[0] + 3), centre(eye[1])))
                        .as("%s ring", finderShape).isEqualTo(255);
                assertThat(alpha(image, centre(eye[0] + 3), centre(eye[1] + 1)))
                        .as("%s gap", finderShape).isZero();
                assertThat(alpha(image, centre(eye[0] + 3), centre(eye[1] + 3)))
                        .as("%s centre", finderShape).isEqualTo(255);
            }
        }
    }

    @Test
    void anOpaqueBackgroundIsStillPaintedEverywhere() throws IOException {
        BufferedImage image = png(style(ModuleShape.SQUARE, FinderShape.SQUARE, null, null, null, false));

        assertThat(alpha(image, 0, 0)).isEqualTo(255);
        assertThat(alpha(image, centre(1), centre(1))).isEqualTo(255);
        assertThat(image.getRGB(centre(1), centre(1)) & 0xffffff).isEqualTo(0xffffff);
    }

    @Test
    void theCustomFinderGapIsTransparentAndTheArtworkKeepsItsRings() throws IOException {
        QrFinderArtwork artwork = new QrFinderArtwork(solidPng(new Color(0xcc, 0x22, 0x22)));
        BufferedImage image = png(style(ModuleShape.SQUARE, FinderShape.CUSTOM, artwork, null, null, true));

        for (int[] eye : new int[][] {{0, 0}, {14, 0}, {0, 14}}) {
            int ring = image.getRGB(centre(eye[0]), centre(eye[1] + 3));
            assertThat(ring >>> 24).isEqualTo(255);
            assertThat(new Color(ring).getRed()).isGreaterThan(150);
            // The whole gap ring, all four sides, is clear.
            for (int i = 1; i <= 5; i++) {
                assertThat(alpha(image, centre(eye[0] + i), centre(eye[1] + 1))).isZero();
                assertThat(alpha(image, centre(eye[0] + i), centre(eye[1] + 5))).isZero();
                assertThat(alpha(image, centre(eye[0] + 1), centre(eye[1] + i))).isZero();
                assertThat(alpha(image, centre(eye[0] + 5), centre(eye[1] + i))).isZero();
            }
            assertThat(alpha(image, centre(eye[0] + 3), centre(eye[1] + 3))).isEqualTo(255);
        }
    }

    @Test
    void theLogoKeepsItsOwnPlate() throws IOException {
        QrLogo logo = new QrLogo(solidPng(new Color(0x22, 0x88, 0x44)), 20, 30, "#ffeecc", 0);
        BufferedImage image = png(new QrStyle(SIZE, MARGIN, ErrorCorrection.HIGH, "#101820", null,
                "#ffffff", null, ModuleShape.SQUARE, FinderShape.SQUARE, null, null, logo, null, true));

        // The plate's padding, between the logo and the plate's edge, is painted.
        int side = (int) Math.round(SIZE * 0.2);
        int padding = (int) Math.floor(side * 0.3);
        int x = (SIZE - side) / 2 - padding / 2;
        int pixel = image.getRGB(x, SIZE / 2);
        assertThat(pixel >>> 24).isEqualTo(255);
        assertThat(pixel & 0xffffff).isEqualTo(0xffeecc);
    }

    @Test
    void aFramedCodeLeavesTheCardTransparentButDrawsTheBorderAndText() throws IOException {
        // The card colour asks for dark; transparency overrides it, so the declared background
        // is what the quiet zone check measures against and the frame is accepted.
        QrFrame frame = new QrFrame("Scan me", null, "#1f2937", null, null,
                null, null, 2, 0, 6, null, 5);
        BufferedImage image = png(style(ModuleShape.SQUARE, FinderShape.SQUARE, null, null, frame, true));

        assertThat(alpha(image, 0, 0)).as("bleed").isZero();
        assertThat(alpha(image, image.getWidth() / 2, image.getHeight() - 1)).as("bleed").isZero();
        // The border runs down the left side, just inside the bleed.
        int bleed = (int) Math.round(SIZE * 0.05);
        int border = (int) Math.round(SIZE * 0.02);
        assertThat(alpha(image, bleed + border / 2, image.getHeight() / 2)).as("border").isEqualTo(255);
        // Between the border and the code: the card's padding, unpainted.
        assertThat(alpha(image, bleed + border + 3, image.getHeight() / 2)).as("padding").isZero();
        // Somewhere in the title, there is ink.
        long titleInk = 0;
        for (int y = bleed + border; y < bleed + border + SIZE / 4; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (alpha(image, x, y) == 255) {
                    titleInk++;
                }
            }
        }
        assertThat(titleInk).isPositive();
    }

    @Test
    void theSvgHasNoBackgroundAndMasksEachEyesGap() {
        String svg = svg(style(ModuleShape.SQUARE, FinderShape.ROUNDED, null, null, null, true));

        assertThat(svg).doesNotContain("fill=\"#ffffff\"");
        // The only canvas-sized rects are the masks' white fields, which paint nothing.
        assertThat(svg.replaceAll("(?s)<mask.*?</mask>", ""))
                .doesNotContain("<rect width=\"" + SIZE + "\" height=\"" + SIZE + "\"");
        assertThat(svg).contains("<mask id=\"finder-gap-0\"", "<mask id=\"finder-gap-1\"",
                "<mask id=\"finder-gap-2\"");
        assertThat(svg).contains("mask=\"url(#finder-gap-0)\"");
    }

    @Test
    void anOpaqueSvgStillHasItsBackgroundRect() {
        String svg = svg(style(ModuleShape.SQUARE, FinderShape.ROUNDED, null, null, null, false));

        assertThat(svg).contains("<rect width=\"" + SIZE + "\" height=\"" + SIZE + "\" fill=\"#ffffff\"/>");
        assertThat(svg).doesNotContain("<mask");
    }

    @Test
    void theSvgMasksTheCustomFinderGapOutOfTheArtwork() throws IOException {
        QrFinderArtwork artwork = new QrFinderArtwork(solidPng(new Color(0xcc, 0x22, 0x22)));
        String svg = svg(style(ModuleShape.SQUARE, FinderShape.CUSTOM, artwork, null, null, true));

        assertThat(svg).contains("<mask id=\"finder-gap\"");
        assertThat(svg).contains("<g mask=\"url(#finder-gap)\">");
        assertThat(svg).doesNotContain("fill=\"#ffffff\"");
    }

    @Test
    void theSvgOfAFramedCodeHasNoCard() {
        QrFrame frame = new QrFrame("Scan me", null, null, null, null,
                null, null, null, null, null, null, null);
        String svg = svg(style(ModuleShape.SQUARE, FinderShape.SQUARE, null, null, frame, true));

        assertThat(svg).doesNotContain("fill=\"#ffffff\"");
        assertThat(svg).contains("<path d=\"M");
    }

    @Test
    void rejectsABackgroundImageWithATransparentBackground() throws IOException {
        QrStyle style = style(ModuleShape.SQUARE, FinderShape.SQUARE, null,
                new QrBackgroundImage(solidPng(Color.ORANGE)), null, true);

        assertThatThrownBy(() -> service.render(new QrCodeRequest(DATA, style), QrFormat.PNG))
                .isInstanceOf(BackgroundImageException.class)
                .hasMessageContaining("style.transparentBackground");
        assertThatThrownBy(() -> service.validate(style))
                .isInstanceOf(BackgroundImageException.class);
    }

    @Test
    void decodesWhenFlattenedOntoTheBackgroundColour() throws IOException {
        String data = "https://example.com/transparent?shape=liquid";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", null, "#f4f1e8",
                null, ModuleShape.LIQUID, FinderShape.ROUNDED, "#3158c4", null, null, null, true);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(new QrCodeRequest(data, style), QrFormat.PNG)));

        assertThat(alpha(image, 0, 0)).isZero();
        assertThat(decode(flatten(image, new Color(0xf4, 0xf1, 0xe8)), false)).isEqualTo(data);
    }

    @Test
    void aLightOnDarkCodeDecodesOnTheDarkItWasMadeFor() throws IOException {
        String data = "https://example.com/transparent?light=on-dark";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#f8fafc", null, "#0f172a",
                null, ModuleShape.SQUARE, FinderShape.SQUARE, null, null, null, null, true);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(new QrCodeRequest(data, style), QrFormat.PNG)));

        assertThat(decode(flatten(image, new Color(0x0f, 0x17, 0x2a)), true)).isEqualTo(data);
    }

    private BufferedImage png(QrStyle style) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(
                service.render(new QrCodeRequest(DATA, style), QrFormat.PNG)));
    }

    private String svg(QrStyle style) {
        return new String(service.render(new QrCodeRequest(DATA, style), QrFormat.SVG),
                StandardCharsets.UTF_8);
    }

    private static QrStyle style(ModuleShape moduleShape, FinderShape finderShape,
            QrFinderArtwork artwork, QrBackgroundImage backgroundImage, QrFrame frame,
            boolean transparent) {
        return new QrStyle(SIZE, MARGIN, ErrorCorrection.LOW, "#101820", null, "#ffffff",
                backgroundImage, moduleShape, finderShape, "#101820", artwork, null, frame, transparent);
    }

    /** The matrix the service encodes for {@link #DATA}: version 1, no quiet zone. */
    private static BitMatrix matrix() {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.MARGIN, 0);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(DATA, BarcodeFormat.QR_CODE, 0, 0, hints);
            assertThat(matrix.getWidth()).isEqualTo(21);
            return matrix;
        } catch (WriterException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean inFinder(int row, int column, int dimension) {
        return (row < 7 && column < 7)
                || (row < 7 && column >= dimension - 7)
                || (row >= dimension - 7 && column < 7);
    }

    private static int centre(int module) {
        return (module + MARGIN) * MODULE + MODULE / 2;
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static BufferedImage flatten(BufferedImage image, Color background) {
        BufferedImage flat = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flat.createGraphics();
        try {
            graphics.setColor(background);
            graphics.fillRect(0, 0, flat.getWidth(), flat.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return flat;
    }

    private static String decode(BufferedImage image, boolean inverted) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        if (inverted) {
            hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
        }
        try {
            return new MultiFormatReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))),
                    hints).getText();
        } catch (NotFoundException e) {
            throw new AssertionError("The flattened image did not decode as a QR code.", e);
        }
    }

    private static String solidPng(Color color) throws IOException {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 40, 40);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}

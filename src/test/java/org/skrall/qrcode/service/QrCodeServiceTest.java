package org.skrall.qrcode.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
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
import java.util.function.IntPredicate;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.FinderArtworkException;
import org.skrall.qrcode.LogoException;
import org.skrall.qrcode.QrEncodingException;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.FinderShape;
import org.skrall.qrcode.model.ModuleShape;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFinderArtwork;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.model.QrLogo;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.render.PngQrRenderer;
import org.skrall.qrcode.render.SvgQrRenderer;

class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService(new PngQrRenderer(), new SvgQrRenderer());

    @Test
    void rendersPngAtTheRequestedSize() throws IOException {
        byte[] png = service.render(request("https://example.com", style(256)), QrFormat.PNG);

        assertThat(png).isNotEmpty();
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(new String(png, 1, 3, StandardCharsets.US_ASCII)).isEqualTo("PNG");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(256);
        assertThat(image.getHeight()).isEqualTo(256);
    }

    @Test
    void rendersSvgWithTheRequestedSize() {
        String svg = new String(service.render(request("https://example.com", style(320)), QrFormat.SVG),
                StandardCharsets.UTF_8);

        assertThat(svg).startsWith("<?xml");
        assertThat(svg).contains("<svg");
        assertThat(svg).contains("width=\"320\"");
        assertThat(svg).contains("viewBox=\"0 0 320 320\"");
        assertThat(svg).contains("</svg>");
    }

    @Test
    void everyModuleAndFinderShapeRenders() {
        for (ModuleShape moduleShape : ModuleShape.values()) {
            for (FinderShape finderShape : FinderShape.values()) {
                QrStyle style = new QrStyle(256, 2, ErrorCorrection.MEDIUM, "#101820", "#fefefe",
                        moduleShape, finderShape, "#2a5db0", null, null);

                assertThat(service.render(request("shape check", style), QrFormat.PNG)).isNotEmpty();
                assertThat(service.render(request("shape check", style), QrFormat.SVG)).isNotEmpty();
            }
        }
    }

    @Test
    void embedsTheLogoInTheMiddle() throws IOException {
        String dataUrl = solidPng(new Color(0xcc, 0x22, 0x22));
        QrStyle style = new QrStyle(300, 4, ErrorCorrection.HIGH, "#000000", "#ffffff",
                ModuleShape.ROUNDED, FinderShape.ROUNDED, null, null,
                new QrLogo(dataUrl, 20, 8, "#ffffff", 25));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request("https://example.com", style), QrFormat.PNG)));

        Color centre = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
        assertThat(centre.getRed()).isGreaterThan(150);
        assertThat(centre.getGreen()).isLessThan(100);
        assertThat(centre.getBlue()).isLessThan(100);
    }

    @Test
    void embedsTheLogoInSvgAsADataUrl() throws IOException {
        String dataUrl = solidPng(new Color(0x22, 0x88, 0x44));
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.HIGH, "#000000", "#ffffff",
                ModuleShape.DOTS, FinderShape.CIRCLE, null, null,
                new QrLogo(dataUrl, 20, 8, "#ffffff", 25));

        String svg = new String(service.render(request("logo", style), QrFormat.SVG), StandardCharsets.UTF_8);

        assertThat(svg).contains("<image");
        assertThat(svg).contains(dataUrl);
    }

    @Test
    void theRoundedCodeWithALogoStillDecodes() throws IOException {
        String data = "https://example.com/rounded?logo=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.HIGH, "#101820", "#ffffff",
                ModuleShape.ROUNDED, FinderShape.ROUNDED, "#3158c4", null,
                new QrLogo(solidPng(new Color(0xcc, 0x22, 0x22)), 18, 8, "#ffffff", 25));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        // The shapes and the logo are only worth offering if a reader still recovers the payload.
        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theDotCodeWithCircleEyesStillDecodes() throws IOException {
        String data = "WIFI:T:WPA;S:Cafe;P:espresso;;";
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.QUARTILE, "#000000", "#ffffff",
                ModuleShape.DOTS, FinderShape.CIRCLE, null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theStarCodeWithLeafEyesStillDecodes() throws IOException {
        String data = "https://example.com/star?leaf=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", "#ffffff",
                ModuleShape.STAR, FinderShape.LEAF, "#7c3aed", null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theLiquidCodeStillDecodes() throws IOException {
        String data = "https://example.com/liquid?connected=modules";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", "#ffffff",
                ModuleShape.LIQUID, FinderShape.ROUNDED, "#3158c4", null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theLiquidCodeWithALogoStillDecodes() throws IOException {
        String data = "https://example.com/liquid?logo=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.HIGH, "#101820", "#ffffff",
                ModuleShape.LIQUID, FinderShape.ROUNDED, "#3158c4", null,
                new QrLogo(solidPng(new Color(0xcc, 0x22, 0x22)), 18, 8, "#ffffff", 25));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theStarCodeWithDiamondEyesStillDecodes() throws IOException {
        String data = "https://example.com/star?diamond=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", "#ffffff",
                ModuleShape.STAR, FinderShape.DIAMOND, "#7c3aed", null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    private static String decode(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try {
            Result result = new MultiFormatReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))),
                    hints);
            return result.getText();
        } catch (NotFoundException e) {
            throw new AssertionError("The rendered image did not decode as a QR code.", e);
        }
    }

    @Test
    void fillsTheEyesWithFinderArtwork() throws IOException {
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.MEDIUM, "#101820", "#ffffff",
                ModuleShape.SQUARE, FinderShape.CUSTOM, "#101820",
                new QrFinderArtwork(solidPng(new Color(0xcc, 0x22, 0x22))), null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request("artwork", style), QrFormat.PNG)));

        long red = countPixels(image, 0, 0, image.getWidth() / 3, image.getHeight() / 3,
                argb -> {
                    Color color = new Color(argb);
                    return color.getRed() > 150 && color.getGreen() < 100 && color.getBlue() < 100;
                });
        assertThat(red).isPositive();
    }

    @Test
    void lightFinderArtworkIsDarkenedSoTheRingsStayVisible() throws IOException {
        // The modules and the corner colour are white too, so any dark pixel in the top-left
        // quadrant can only come from the adjusted artwork.
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.MEDIUM, "#ffffff", "#ffffff",
                ModuleShape.SQUARE, FinderShape.CUSTOM, "#ffffff",
                new QrFinderArtwork(solidPng(Color.WHITE)), null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request("artwork", style), QrFormat.PNG)));

        long dark = countPixels(image, 0, 0, image.getWidth() / 3, image.getHeight() / 3,
                argb -> {
                    Color color = new Color(argb);
                    return color.getRed() < 160 && color.getGreen() < 160 && color.getBlue() < 160;
                });
        assertThat(dark).isPositive();
    }

    @Test
    void transparentFinderArtworkFallsBackToTheCornerColour() throws IOException {
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.MEDIUM, "#101820", "#ffffff",
                ModuleShape.SQUARE, FinderShape.CUSTOM, "#cc2222",
                new QrFinderArtwork(transparentPng()), null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request("artwork", style), QrFormat.PNG)));

        long red = countPixels(image, 0, 0, image.getWidth() / 3, image.getHeight() / 3,
                argb -> {
                    Color color = new Color(argb);
                    return color.getRed() > 150 && color.getGreen() < 100 && color.getBlue() < 100;
                });
        assertThat(red).isPositive();
    }

    @Test
    void embedsFinderArtworkInSvg() throws IOException {
        QrStyle style = new QrStyle(320, 4, ErrorCorrection.MEDIUM, "#101820", "#ffffff",
                ModuleShape.SQUARE, FinderShape.CUSTOM, "#101820",
                new QrFinderArtwork(solidPng(new Color(0x22, 0x88, 0x44))), null);

        String svg = new String(service.render(request("artwork", style), QrFormat.SVG),
                StandardCharsets.UTF_8);

        assertThat(svg).contains("clip-path=\"url(#finder-outer)\"");
        assertThat(svg).contains("clip-path=\"url(#finder-centre)\"");
        assertThat(svg).contains("<image");
        assertThat(svg).contains("data:image/png;base64,");
    }

    @Test
    void theCustomFinderArtworkCodeStillDecodes() throws IOException {
        String data = "https://example.com/artwork?eyes=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", "#ffffff",
                ModuleShape.ROUNDED, FinderShape.CUSTOM, "#101820",
                new QrFinderArtwork(stripedPng(new Color(0x2a, 0x5d, 0xb0), new Color(0x6d, 0x28, 0x8f))),
                null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void aCustomFinderWithoutArtworkFallsBackToSquare() throws IOException {
        String data = "https://example.com/artwork?none=1";
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.QUARTILE, "#101820", "#ffffff",
                ModuleShape.ROUNDED, FinderShape.CUSTOM, "#3158c4", null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void rejectsFinderArtworkThatIsNotADataUrl() {
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.MEDIUM, null, null, null,
                FinderShape.CUSTOM, null, new QrFinderArtwork("not-a-data-url"), null);

        assertThatThrownBy(() -> service.render(request("artwork", style), QrFormat.PNG))
                .isInstanceOf(FinderArtworkException.class)
                .hasMessageContaining("style.finderArtwork.dataUrl");
    }

    @Test
    void rejectsALogoThatIsNotADataUrl() {
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.HIGH, null, null, null, null, null, null,
                new QrLogo("not-a-data-url", null, null, null, null));

        assertThatThrownBy(() -> service.render(request("logo", style), QrFormat.PNG))
                .isInstanceOf(LogoException.class)
                .hasMessageContaining("data URL");
    }

    @Test
    void rejectsALogoThatIsNotAnImage() {
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString("nope".getBytes(StandardCharsets.UTF_8));
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.HIGH, null, null, null, null, null, null,
                new QrLogo(dataUrl, null, null, null, null));

        assertThatThrownBy(() -> service.render(request("logo", style), QrFormat.PNG))
                .isInstanceOf(LogoException.class)
                .hasMessageContaining("PNG, JPEG or GIF");
    }

    @Test
    void rejectsContentThatDoesNotFit() {
        String tooLong = "a".repeat(4000);

        assertThatThrownBy(() -> service.render(request(tooLong, style(256)), QrFormat.PNG))
                .isInstanceOf(QrEncodingException.class)
                .hasMessageContaining("too long");
    }

    private static long countPixels(
            BufferedImage image, int x, int y, int width, int height, IntPredicate matches) {

        long count = 0;
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                if (matches.test(image.getRGB(column, row))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static QrCodeRequest request(String data, QrStyle style) {
        return new QrCodeRequest(data, style);
    }

    private static QrStyle style(int size) {
        return new QrStyle(size, 4, ErrorCorrection.MEDIUM, null, null, null, null, null, null, null);
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
        return dataUrl(image);
    }

    private static String transparentPng() throws IOException {
        return dataUrl(new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB));
    }

    private static String stripedPng(Color first, Color second) throws IOException {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int x = 0; x < 40; x += 8) {
                graphics.setColor((x / 8) % 2 == 0 ? first : second);
                graphics.fillRect(x, 0, 8, 40);
            }
        } finally {
            graphics.dispose();
        }
        return dataUrl(image);
    }

    private static String dataUrl(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}

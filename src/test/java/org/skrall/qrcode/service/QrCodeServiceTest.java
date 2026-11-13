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
import org.skrall.qrcode.BackgroundImageException;
import org.skrall.qrcode.FinderArtworkException;
import org.skrall.qrcode.FrameException;
import org.skrall.qrcode.GradientException;
import org.skrall.qrcode.LogoException;
import org.skrall.qrcode.QrEncodingException;
import org.skrall.qrcode.check.ScanChecker;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.FinderShape;
import org.skrall.qrcode.model.GradientType;
import org.skrall.qrcode.model.ModuleShape;
import org.skrall.qrcode.model.QrBackgroundImage;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFinderArtwork;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.model.QrFrame;
import org.skrall.qrcode.model.QrGradient;
import org.skrall.qrcode.model.QrLogo;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.render.PngQrRenderer;
import org.skrall.qrcode.render.SvgQrRenderer;

class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService(new PngQrRenderer(), new SvgQrRenderer(), new ScanChecker());

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
                QrStyle style = new QrStyle(256, 2, ErrorCorrection.MEDIUM, "#101820", null, "#fefefe",
                        null, moduleShape, finderShape, "#2a5db0", null, null, null);

                assertThat(service.render(request("shape check", style), QrFormat.PNG)).isNotEmpty();
                assertThat(service.render(request("shape check", style), QrFormat.SVG)).isNotEmpty();
            }
        }
    }

    @Test
    void embedsTheLogoInTheMiddle() throws IOException {
        String dataUrl = solidPng(new Color(0xcc, 0x22, 0x22));
        QrStyle style = new QrStyle(300, 4, ErrorCorrection.HIGH, "#000000", null, "#ffffff",
                null, ModuleShape.ROUNDED, FinderShape.ROUNDED, null, null,
                new QrLogo(dataUrl, 20, 8, "#ffffff", 25), null);

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
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.HIGH, "#000000", null, "#ffffff",
                null, ModuleShape.DOTS, FinderShape.CIRCLE, null, null,
                new QrLogo(dataUrl, 20, 8, "#ffffff", 25), null);

        String svg = new String(service.render(request("logo", style), QrFormat.SVG), StandardCharsets.UTF_8);

        assertThat(svg).contains("<image");
        assertThat(svg).contains(dataUrl);
    }

    @Test
    void theRoundedCodeWithALogoStillDecodes() throws IOException {
        String data = "https://example.com/rounded?logo=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.HIGH, "#101820", null, "#ffffff",
                null, ModuleShape.ROUNDED, FinderShape.ROUNDED, "#3158c4", null,
                new QrLogo(solidPng(new Color(0xcc, 0x22, 0x22)), 18, 8, "#ffffff", 25), null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        // The shapes and the logo are only worth offering if a reader still recovers the payload.
        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theDotCodeWithCircleEyesStillDecodes() throws IOException {
        String data = "WIFI:T:WPA;S:Cafe;P:espresso;;";
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.QUARTILE, "#000000", null, "#ffffff",
                null, ModuleShape.DOTS, FinderShape.CIRCLE, null, null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theStarCodeWithLeafEyesStillDecodes() throws IOException {
        String data = "https://example.com/star?leaf=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", null, "#ffffff",
                null, ModuleShape.STAR, FinderShape.LEAF, "#7c3aed", null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theLiquidCodeStillDecodes() throws IOException {
        String data = "https://example.com/liquid?connected=modules";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", null, "#ffffff",
                null, ModuleShape.LIQUID, FinderShape.ROUNDED, "#3158c4", null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theLiquidCodeWithALogoStillDecodes() throws IOException {
        String data = "https://example.com/liquid?logo=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.HIGH, "#101820", null, "#ffffff",
                null, ModuleShape.LIQUID, FinderShape.ROUNDED, "#3158c4", null,
                new QrLogo(solidPng(new Color(0xcc, 0x22, 0x22)), 18, 8, "#ffffff", 25), null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theStarCodeWithDiamondEyesStillDecodes() throws IOException {
        String data = "https://example.com/star?diamond=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", null, "#ffffff",
                null, ModuleShape.STAR, FinderShape.DIAMOND, "#7c3aed", null, null, null);

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
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.MEDIUM, "#101820", null, "#ffffff",
                null, ModuleShape.SQUARE, FinderShape.CUSTOM, "#101820",
                new QrFinderArtwork(solidPng(new Color(0xcc, 0x22, 0x22))), null, null);

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
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.MEDIUM, "#ffffff", null, "#ffffff",
                null, ModuleShape.SQUARE, FinderShape.CUSTOM, "#ffffff",
                new QrFinderArtwork(solidPng(Color.WHITE)), null, null);

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
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.MEDIUM, "#101820", null, "#ffffff",
                null, ModuleShape.SQUARE, FinderShape.CUSTOM, "#cc2222",
                new QrFinderArtwork(transparentPng()), null, null);

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
        QrStyle style = new QrStyle(320, 4, ErrorCorrection.MEDIUM, "#101820", null, "#ffffff",
                null, ModuleShape.SQUARE, FinderShape.CUSTOM, "#101820",
                new QrFinderArtwork(solidPng(new Color(0x22, 0x88, 0x44))), null, null);

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
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", null, "#ffffff",
                null, ModuleShape.ROUNDED, FinderShape.CUSTOM, "#101820",
                new QrFinderArtwork(stripedPng(new Color(0x2a, 0x5d, 0xb0), new Color(0x6d, 0x28, 0x8f))),
                null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void aCustomFinderWithoutArtworkFallsBackToSquare() throws IOException {
        String data = "https://example.com/artwork?none=1";
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.QUARTILE, "#101820", null, "#ffffff",
                null, ModuleShape.ROUNDED, FinderShape.CUSTOM, "#3158c4", null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void drawsTheBackgroundImageBehindTheCode() throws IOException {
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.MEDIUM, "#101820", null, "#ffffff",
                new QrBackgroundImage(solidPng(new Color(0xcc, 0x22, 0x22))),
                ModuleShape.SQUARE, FinderShape.SQUARE, "#101820", null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request("background", style), QrFormat.PNG)));

        // The corner of the quiet zone is outside the code, so it can only be the image.
        Color corner = new Color(image.getRGB(5, 5));
        assertThat(corner.getRed()).isGreaterThan(200);
        assertThat(corner.getGreen()).isLessThan(100);
        assertThat(corner.getBlue()).isLessThan(100);
    }

    @Test
    void darkBackgroundImageIsWashedSoTheModulesStayReadable() throws IOException {
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.MEDIUM, "#101820", null, "#ffffff",
                new QrBackgroundImage(solidPng(Color.BLACK)),
                ModuleShape.SQUARE, FinderShape.SQUARE, "#101820", null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request("background", style), QrFormat.PNG)));

        Color corner = new Color(image.getRGB(5, 5));
        assertThat(corner.getRed()).isGreaterThan(80);
        assertThat(corner.getGreen()).isGreaterThan(80);
        assertThat(corner.getBlue()).isGreaterThan(80);
    }

    @Test
    void transparentBackgroundImageFallsBackToTheBackgroundColour() throws IOException {
        QrStyle style = new QrStyle(512, 4, ErrorCorrection.MEDIUM, "#101820", null, "#ffffff",
                new QrBackgroundImage(transparentPng()),
                ModuleShape.SQUARE, FinderShape.SQUARE, "#101820", null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request("background", style), QrFormat.PNG)));

        Color corner = new Color(image.getRGB(5, 5));
        assertThat(corner.getRed()).isGreaterThan(240);
        assertThat(corner.getGreen()).isGreaterThan(240);
        assertThat(corner.getBlue()).isGreaterThan(240);
    }

    @Test
    void embedsTheBackgroundImageInSvg() throws IOException {
        QrStyle style = new QrStyle(320, 4, ErrorCorrection.MEDIUM, "#101820", null, "#ffffff",
                new QrBackgroundImage(solidPng(new Color(0x22, 0x88, 0x44))),
                ModuleShape.SQUARE, FinderShape.SQUARE, "#101820", null, null, null);

        String svg = new String(service.render(request("background", style), QrFormat.SVG),
                StandardCharsets.UTF_8);

        assertThat(svg).contains("<image x=\"0\" y=\"0\" width=\"320\" height=\"320\"");
        assertThat(svg).contains("preserveAspectRatio=\"xMidYMid slice\"");
        assertThat(svg).contains("data:image/png;base64,");
    }

    @Test
    void theCodeWithABackgroundImageStillDecodes() throws IOException {
        String data = "https://example.com/background?image=1";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", null, "#ffffff",
                new QrBackgroundImage(stripedPng(
                        new Color(0xff, 0xcc, 0x66), new Color(0x99, 0xcc, 0xff))),
                ModuleShape.ROUNDED, FinderShape.ROUNDED, "#3158c4", null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void aLightCornerColourOverABackgroundImageStillDecodes() throws IOException {
        // The eyes are lighter than the modules, so washing only against the foreground would
        // leave the image around them too close to the rings.
        String data = "https://example.com/background?corners=light";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", null, "#ffffff",
                new QrBackgroundImage(solidPng(new Color(0x70, 0x70, 0x70))),
                ModuleShape.SQUARE, FinderShape.SQUARE, "#3158c4", null, null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void customCornerArtworkOverABackgroundImageStillDecodes() throws IOException {
        // Artwork is only clamped against the background colour, so it can be lighter than the
        // foreground; the wash has to clear the artwork as well.
        String data = "https://example.com/background?corners=artwork";
        QrStyle style = new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", null, "#ffffff",
                new QrBackgroundImage(solidPng(new Color(0x70, 0x70, 0x70))),
                ModuleShape.SQUARE, FinderShape.CUSTOM, "#101820",
                new QrFinderArtwork(stripedPng(new Color(0x2a, 0x5d, 0xb0), new Color(0x6d, 0x28, 0x8f))), null, null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theLinearGradientCodeStillDecodes() throws IOException {
        String data = "https://example.com/gradient?type=linear";
        QrStyle style = gradientStyle(ModuleShape.ROUNDED, FinderShape.ROUNDED,
                new QrGradient(GradientType.LINEAR, "#7c3aed", "#0f766e", 135, true), null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theRadialGradientLiquidCodeStillDecodes() throws IOException {
        String data = "https://example.com/gradient?type=radial";
        QrStyle style = gradientStyle(ModuleShape.LIQUID, FinderShape.CIRCLE,
                new QrGradient(GradientType.RADIAL, "#101820", "#3158c4", null, false), null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void theGradientRunsAcrossTheCode() throws IOException {
        // 90 degrees runs left to right, so red should gather on the left and blue on the right.
        QrStyle style = gradientStyle(ModuleShape.SQUARE, FinderShape.SQUARE,
                new QrGradient(GradientType.LINEAR, "#b00000", "#0000b0", 90, false), null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request("left to right", style), QrFormat.PNG)));

        int third = image.getWidth() / 3;
        IntPredicate red = argb -> ((argb >>> 16) & 0xff) > 0x60 && (argb & 0xff) < 0x40;
        IntPredicate blue = argb -> (argb & 0xff) > 0x60 && ((argb >>> 16) & 0xff) < 0x40;
        int height = image.getHeight();
        assertThat(countPixels(image, 0, 0, third, height, red))
                .isGreaterThan(countPixels(image, 2 * third, 0, third, height, red));
        assertThat(countPixels(image, 2 * third, 0, third, height, blue))
                .isGreaterThan(countPixels(image, 0, 0, third, height, blue));
    }

    @Test
    void theEyesKeepTheirColourUnlessTheGradientIncludesThem() throws IOException {
        QrGradient modulesOnly = new QrGradient(GradientType.LINEAR, "#b00000", "#0000b0", 90, false);
        QrGradient withFinders = new QrGradient(GradientType.LINEAR, "#b00000", "#0000b0", 90, true);

        // The top-right eye sits at the blue end. "eyes" fits version 1: 21 modules plus a quiet
        // zone of four either side, so the eye's centre block is around module (21.5, 7.5).
        BufferedImage plain = ImageIO.read(new ByteArrayInputStream(service.render(
                request("eyes", gradientStyle(ModuleShape.SQUARE, FinderShape.SQUARE, modulesOnly, null)),
                QrFormat.PNG)));
        BufferedImage painted = ImageIO.read(new ByteArrayInputStream(service.render(
                request("eyes", gradientStyle(ModuleShape.SQUARE, FinderShape.SQUARE, withFinders, null)),
                QrFormat.PNG)));

        double scale = plain.getWidth() / 29.0;
        int x = (int) (21.5 * scale);
        int y = (int) (7.5 * scale);
        assertThat(new Color(plain.getRGB(x, y))).isEqualTo(new Color(0x10, 0x18, 0x20));
        Color ring = new Color(painted.getRGB(x, y));
        assertThat(ring.getBlue()).isGreaterThan(ring.getRed());
    }

    @Test
    void writesTheGradientGeometryIntoSvg() {
        QrStyle linear = new QrStyle(300, 4, ErrorCorrection.MEDIUM, "#101820",
                new QrGradient(GradientType.LINEAR, "#b00000", "#0000b0", 90, true), "#ffffff",
                null, ModuleShape.SQUARE, FinderShape.SQUARE, null, null, null, null);
        QrStyle radial = new QrStyle(300, 4, ErrorCorrection.MEDIUM, "#101820",
                new QrGradient(GradientType.RADIAL, "#b00000", "#0000b0", null, false), "#ffffff",
                null, ModuleShape.SQUARE, FinderShape.SQUARE, "#101820", null, null, null);

        String linearSvg = new String(service.render(request("svg", linear), QrFormat.SVG), StandardCharsets.UTF_8);
        String radialSvg = new String(service.render(request("svg", radial), QrFormat.SVG), StandardCharsets.UTF_8);

        assertThat(linearSvg)
                .contains("<linearGradient id=\"qr-gradient\" gradientUnits=\"userSpaceOnUse\""
                        + " x1=\"0\" y1=\"150\" x2=\"300\" y2=\"150\">")
                .contains("<stop offset=\"0\" stop-color=\"#b00000\"/>")
                .contains("<stop offset=\"1\" stop-color=\"#0000b0\"/>")
                .contains("<g fill=\"url(#qr-gradient)\">")
                .contains("height=\"72.41\" fill=\"url(#qr-gradient)\"/>");
        // The eyes are plain squares here, so their rings are the only rects with a solid fill.
        assertThat(linearSvg).doesNotContain("fill=\"#101820\"");

        assertThat(radialSvg)
                .contains("<radialGradient id=\"qr-gradient\" gradientUnits=\"userSpaceOnUse\""
                        + " cx=\"150\" cy=\"150\" r=\"212.13\">")
                .contains("<g fill=\"url(#qr-gradient)\">")
                .contains("fill=\"#101820\"");
    }

    @Test
    void aGradientOverABackgroundImageStillDecodes() throws IOException {
        String data = "https://example.com/gradient?background=image";
        QrStyle style = gradientStyle(ModuleShape.ROUNDED, FinderShape.ROUNDED,
                new QrGradient(GradientType.LINEAR, "#101820", "#3158c4", 45, true),
                new QrBackgroundImage(solidPng(new Color(0x70, 0x70, 0x70))));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void rejectsAGradientStopTooCloseToTheBackground() {
        // Light grey on white would leave the end of the gradient, and that corner, unreadable.
        QrStyle style = gradientStyle(ModuleShape.SQUARE, FinderShape.SQUARE,
                new QrGradient(GradientType.LINEAR, "#101820", "#d1d5db", 135, false), null);

        assertThatThrownBy(() -> service.render(request("gradient", style), QrFormat.PNG))
                .isInstanceOf(GradientException.class)
                .hasMessageContaining("style.gradient.end #d1d5db is too close to the background #ffffff");
    }

    @Test
    void rejectsAGradientThatCrossesTheBackground() {
        // Each stop contrasts with mid grey on its own, but the blend passes through it.
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.MEDIUM, null,
                new QrGradient(GradientType.LINEAR, "#000000", "#ffffff", 90, false), "#808080",
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.render(request("gradient", style), QrFormat.PNG))
                .isInstanceOf(GradientException.class)
                .hasMessageContaining("both be darker or both be lighter than the background #808080");
    }

    @Test
    void theFramedCodeStillDecodes() throws IOException {
        String data = "https://example.com/frame?card=1";
        QrStyle style = framed(4, new QrFrame("Scan me", "Menu, hours and reservations",
                null, null, null, null, null, null, null, null, null, null));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void aFrameGrowsTheImageAroundTheCode() throws IOException {
        // 400 px code: padding 6% = 24, border 2% = 8, bleed 5% = 20, so 52 px on each side.
        QrStyle style = framed(4, new QrFrame("Scan me", null,
                "#fde68a", null, null, null, null, 2, null, 6, 0, 5));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request("frame", style), QrFormat.PNG)));

        assertThat(image.getWidth()).isEqualTo(400 + 2 * 52);
        assertThat(image.getHeight()).isGreaterThan(image.getWidth());
        // The bleed is card colour, and the border starts where it ends.
        assertThat(new Color(image.getRGB(2, 2))).isEqualTo(new Color(0xfd, 0xe6, 0x8a));
        assertThat(new Color(image.getRGB(24, image.getHeight() / 2))).isEqualTo(new Color(0x10, 0x18, 0x20));
    }

    @Test
    void drawsTheTitleAboveTheCodeAndTheCaptionBelow() throws IOException {
        QrStyle titled = framed(4, new QrFrame("Scan me", "Below", null, "#cc2222", "#cc2222",
                null, null, null, null, null, null, null));
        QrStyle plain = framed(4, new QrFrame("  ", null, null, "#cc2222", "#cc2222",
                null, null, null, null, null, null, null));

        BufferedImage withText = ImageIO.read(new ByteArrayInputStream(
                service.render(request("text", titled), QrFormat.PNG)));
        BufferedImage withoutText = ImageIO.read(new ByteArrayInputStream(
                service.render(request("text", plain), QrFormat.PNG)));

        IntPredicate red = argb -> ((argb >>> 16) & 0xff) > 0xa0 && ((argb >>> 8) & 0xff) < 0x60;
        int width = withText.getWidth();
        // Inside the border (2% of 400 = 8 px) and padding (24 px), above and below the code.
        assertThat(countPixels(withText, 40, 34, width - 80, 30, red)).isGreaterThan(200);
        assertThat(countPixels(withText, 40, withText.getHeight() - 50, width - 80, 16, red)).isGreaterThan(50);
        // A blank title is no title: the card is square and the band above the code is empty.
        assertThat(withoutText.getHeight()).isEqualTo(withoutText.getWidth());
        assertThat(countPixels(withoutText, 40, 34, width - 80, 30, red)).isZero();
    }

    @Test
    void writesTheFrameIntoSvgAsOutlines() throws IOException {
        QrStyle style = framed(4, new QrFrame("Scan & go", "Open <daily>", null, "#cc2222", null,
                null, null, null, null, null, null, 5));

        BufferedImage png = ImageIO.read(new ByteArrayInputStream(
                service.render(request("svg frame", style), QrFormat.PNG)));
        String svg = new String(service.render(request("svg frame", style), QrFormat.SVG),
                StandardCharsets.UTF_8);

        assertThat(svg)
                .contains("width=\"" + png.getWidth() + "\" height=\"" + png.getHeight() + "\"")
                .contains("<title>QR code: Scan &amp; go</title>")
                .contains("<desc>Open &lt;daily&gt;</desc>")
                .contains("<g transform=\"translate(52 ")
                .contains("fill=\"#cc2222\" fill-rule=\"nonzero\"/>")
                // The text is outlines, so no viewer font can change it.
                .doesNotContain("<text");
    }

    @Test
    void aFrameWithAGradientAndBackgroundImageStillDecodes() throws IOException {
        String data = "https://example.com/frame?everything=1";
        QrStyle style = new QrStyle(500, 4, ErrorCorrection.QUARTILE, "#101820",
                new QrGradient(GradientType.LINEAR, "#7c3aed", "#0f766e", 135, true), "#ffffff",
                new QrBackgroundImage(solidPng(new Color(0xfa, 0xe8, 0xc8))),
                ModuleShape.ROUNDED, FinderShape.ROUNDED, null, null, null,
                new QrFrame("Scan me", "example.com", "#f5f3ff", null, null,
                        null, null, 2, 6, 6, 2, 3));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void aBorderAtTheMinimumClearanceStillDecodes() throws IOException {
        // Two modules of margin, no padding, and a heavy dark border right against the quiet zone.
        String data = "https://example.com/frame?tight=1";
        QrStyle style = framed(2, new QrFrame(null, null, null, null, null,
                null, null, 6, 0, 0, null, null));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void rejectsAFrameThatCrowdsTheCode() {
        QrStyle style = framed(0, new QrFrame(null, null, null, null, null,
                null, null, 4, 0, 0, null, null));

        assertThatThrownBy(() -> service.render(request("tight", style), QrFormat.PNG))
                .isInstanceOf(FrameException.class)
                .hasMessageContaining("0.0 modules of clear space");
    }

    @Test
    void lightPaddingCountsAsQuietZone() throws IOException {
        // No margin at all, but the light card's padding gives the code its clear space.
        String data = "https://example.com/frame?padding=1";
        QrStyle style = framed(0, new QrFrame(null, null, null, null, null,
                null, null, 2, null, 10, null, null));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                service.render(request(data, style), QrFormat.PNG)));

        assertThat(decode(image)).isEqualTo(data);
    }

    @Test
    void aDarkCardDoesNotCountAsQuietZone() {
        QrStyle style = framed(1, new QrFrame(null, null, "#1f2937", "#ffffff", null,
                null, null, 0, null, 10, null, null));

        assertThatThrownBy(() -> service.render(request("dark card", style), QrFormat.PNG))
                .isInstanceOf(FrameException.class)
                .hasMessageContaining("card colour that contrasts with the modules");
    }

    @Test
    void rejectsTextTheFontCannotDraw() {
        QrStyle style = framed(4, new QrFrame("Scan me 😀", null, null, null, null,
                null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.render(request("emoji", style), QrFormat.PNG))
                .isInstanceOf(FrameException.class)
                .hasMessageContaining("style.frame.title contains \"😀\" (U+1F600)");
    }

    @Test
    void rejectsAFramedImageThatIsTooLarge() {
        QrStyle style = new QrStyle(4096, 4, ErrorCorrection.MEDIUM, null, null, null, null,
                null, null, null, null, null,
                new QrFrame(null, null, null, null, null, null, null, 10, null, 30, 20, 20));

        assertThatThrownBy(() -> service.render(request("huge", style), QrFormat.PNG))
                .isInstanceOf(FrameException.class)
                .hasMessageContaining("The framed image would be");
    }

    @Test
    void rejectsBackgroundImageThatIsNotADataUrl() {
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.MEDIUM, null, null, null,
                new QrBackgroundImage("not-a-data-url"), null, null, null, null, null, null);

        assertThatThrownBy(() -> service.render(request("background", style), QrFormat.PNG))
                .isInstanceOf(BackgroundImageException.class)
                .hasMessageContaining("style.backgroundImage.dataUrl");
    }

    @Test
    void rejectsFinderArtworkThatIsNotADataUrl() {
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.MEDIUM, null, null, null, null, null,
                FinderShape.CUSTOM, null, new QrFinderArtwork("not-a-data-url"), null, null);

        assertThatThrownBy(() -> service.render(request("artwork", style), QrFormat.PNG))
                .isInstanceOf(FinderArtworkException.class)
                .hasMessageContaining("style.finderArtwork.dataUrl");
    }

    @Test
    void rejectsALogoThatIsNotADataUrl() {
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.HIGH, null, null, null, null, null, null, null, null,
                new QrLogo("not-a-data-url", null, null, null, null), null);

        assertThatThrownBy(() -> service.render(request("logo", style), QrFormat.PNG))
                .isInstanceOf(LogoException.class)
                .hasMessageContaining("data URL");
    }

    @Test
    void rejectsALogoThatIsNotAnImage() {
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString("nope".getBytes(StandardCharsets.UTF_8));
        QrStyle style = new QrStyle(256, 4, ErrorCorrection.HIGH, null, null, null, null, null, null, null, null,
                new QrLogo(dataUrl, null, null, null, null), null);

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

    private static QrStyle gradientStyle(
            ModuleShape moduleShape, FinderShape finderShape, QrGradient gradient,
            QrBackgroundImage backgroundImage) {
        return new QrStyle(600, 4, ErrorCorrection.QUARTILE, "#101820", gradient, "#ffffff",
                backgroundImage, moduleShape, finderShape, "#101820", null, null, null);
    }

    private static QrStyle framed(int margin, QrFrame frame) {
        return new QrStyle(400, margin, ErrorCorrection.QUARTILE, "#101820", null, "#ffffff",
                null, ModuleShape.SQUARE, FinderShape.SQUARE, "#101820", null, null, frame);
    }

    private static QrStyle style(int size) {
        return new QrStyle(size, 4, ErrorCorrection.MEDIUM, null, null, null, null, null, null, null, null, null, null);
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

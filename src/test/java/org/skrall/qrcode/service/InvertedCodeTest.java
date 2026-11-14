package org.skrall.qrcode.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.check.ScanChecker;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.FinderShape;
import org.skrall.qrcode.model.GradientType;
import org.skrall.qrcode.model.ModuleShape;
import org.skrall.qrcode.model.QrBackgroundImage;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFinderArtwork;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.model.QrGradient;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.model.ScanCheckResponse;
import org.skrall.qrcode.model.ScanTrial;
import org.skrall.qrcode.model.ScanVerdict;
import org.skrall.qrcode.render.PngQrRenderer;
import org.skrall.qrcode.render.SvgQrRenderer;

/**
 * Light modules on a dark background, as the UI's "Invert colours" action produces them. The
 * colours below are that action's output for the default style and the gradient example in the
 * API docs (see {@code ui/src/qr/invert.spec.ts}).
 */
class InvertedCodeTest {

    private static final String DATA = "https://example.com/inverted?campaign=night";

    private final QrCodeService service =
            new QrCodeService(new PngQrRenderer(), new SvgQrRenderer(), new ScanChecker());

    @Test
    void aPlainInvertedCodeDecodesAndIsMarginal() throws IOException {
        assertInvertedCodeWorks(style("#ffffff", null, "#111827", null, FinderShape.ROUNDED, "#ffffff", null));
    }

    @Test
    void anInvertedCodeWithALightAccentCornerDecodesAndIsMarginal() throws IOException {
        assertInvertedCodeWorks(style("#ffffff", null, "#111827", null, FinderShape.ROUNDED, "#adbeeb", null));
    }

    @Test
    void anInvertedGradientIsAcceptedDecodesAndIsMarginal() throws IOException {
        QrGradient gradient = new QrGradient(GradientType.LINEAR, "#c0a0f6", "#5eebe0", 135, true);

        assertInvertedCodeWorks(style("#ffffff", gradient, "#101820", null, FinderShape.ROUNDED, "#ffffff", null));
    }

    @Test
    void anInvertedCodeOverABackgroundImageDecodes() throws IOException {
        // A reader treats a flat area as paper before it inverts, so a washed image only 96
        // below light modules reads as module colour and fills the quiet zone. Every one of these
        // failed outright before the wash kept an inverted code's image near its background.
        Color[] images = {new Color(0xd0, 0xd8, 0xe0), Color.WHITE, new Color(0x80, 0x80, 0x80)};
        String[][] inks = {{"#ffffff", "#ffffff"}, {"#ffffff", "#adbeeb"}};
        for (Color image : images) {
            for (String[] ink : inks) {
                QrStyle style = style(ink[0], null, "#111827",
                        new QrBackgroundImage(solidPng(image)), FinderShape.ROUNDED, ink[1], null);

                assertInvertedCodeWorks(style);
            }
        }
    }

    @Test
    void anInvertedCodeLightensDarkCornerArtwork() throws IOException {
        QrFinderArtwork artwork = new QrFinderArtwork(solidPng(new Color(0x20, 0x30, 0x60)));

        assertInvertedCodeWorks(style("#ffffff", null, "#111827", null, FinderShape.CUSTOM, "#ffffff", artwork));
    }

    @Test
    void cornerArtworkFollowsTheModulesNotTheBackgroundsOwnBrightness() throws IOException {
        // The background is just below mid-grey but lighter than the modules. Artwork used to be
        // lightened on any background below 128, which put light rings around a dark-module code.
        String data = "https://example.com/artwork?background=grey";
        QrFinderArtwork artwork = new QrFinderArtwork(solidPng(new Color(0xf0, 0xe0, 0xa0)));
        QrStyle style = style("#000000", null, "#7a7a7a", null, FinderShape.CUSTOM, "#000000", artwork);

        assertThat(decode(png(data, style), false)).isEqualTo(data);
    }

    private void assertInvertedCodeWorks(QrStyle style) throws IOException {
        assertThat(decode(png(DATA, style), true)).isEqualTo(DATA);
        assertThat(new String(service.render(new QrCodeRequest(DATA, style), QrFormat.SVG),
                StandardCharsets.UTF_8)).contains("<svg");

        ScanCheckResponse result = service.check(new QrCodeRequest(DATA, style));
        assertThat(result.verdict()).isEqualTo(ScanVerdict.MARGINAL);
        assertThat(result.trials()).filteredOn(trial -> trial.name().equals("rendered"))
                .singleElement().extracting(ScanTrial::decoded).isEqualTo(true);
        assertThat(result.hints()).anySatisfy(hint ->
                assertThat(hint).contains("lighter than the background"));
    }

    private BufferedImage png(String data, QrStyle style) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(
                service.render(new QrCodeRequest(data, style), QrFormat.PNG)));
    }

    private static QrStyle style(String foreground, QrGradient gradient, String background,
            QrBackgroundImage backgroundImage, FinderShape finderShape, String finderColor,
            QrFinderArtwork artwork) {
        return new QrStyle(600, 4, ErrorCorrection.QUARTILE, foreground, gradient, background,
                backgroundImage, ModuleShape.ROUNDED, finderShape, finderColor, artwork, null, null);
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
            // MultiFormatReader rather than QRCodeReader: only it honours ALSO_INVERTED.
            Result result = new MultiFormatReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))),
                    hints);
            return result.getText();
        } catch (NotFoundException e) {
            throw new AssertionError("The rendered image did not decode as a QR code.", e);
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

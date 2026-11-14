package org.skrall.qrcode.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.FinderShape;
import org.skrall.qrcode.model.ModuleShape;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrLogo;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.model.ScanCheckResponse;
import org.skrall.qrcode.model.ScanTrial;
import org.skrall.qrcode.model.ScanVerdict;
import org.skrall.qrcode.render.PngQrRenderer;
import org.skrall.qrcode.render.SvgQrRenderer;
import org.skrall.qrcode.service.QrCodeService;

/**
 * The verdicts here were calibrated against every module and corner shape, logos from 15% to 35%
 * at each error correction level, grey modules and thin quiet zones. Each test pins one side of
 * a boundary the check is meant to find.
 */
class ScanCheckerTest {

    private static final String URL = "https://example.com/some/page?campaign=autumn";

    private final QrCodeService service =
            new QrCodeService(new PngQrRenderer(), new SvgQrRenderer(), new ScanChecker());

    @Test
    void aPlainCodePassesEveryTrialWithoutHints() {
        ScanCheckResponse result = check(URL, QrStyle.EMPTY);

        assertThat(result.verdict()).isEqualTo(ScanVerdict.PASS);
        assertThat(result.trials()).extracting(ScanTrial::name)
                .containsExactly("rendered", "small", "blurred");
        assertThat(result.trials()).allMatch(ScanTrial::decoded);
        assertThat(result.hints()).isEmpty();
        assertThat(result.modules()).isEqualTo(17 + 4 * result.version());
    }

    @Test
    void aLogoWithinHighErrorCorrectionPasses() throws IOException {
        ScanCheckResponse result = check(URL, style(600, 4, ErrorCorrection.HIGH, "#000000",
                "#ffffff", ModuleShape.ROUNDED, FinderShape.ROUNDED, logo(25)));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.PASS);
    }

    @Test
    void aLogoTooLargeForItsErrorCorrectionFails() throws IOException {
        ScanCheckResponse result = check(URL, style(600, 4, ErrorCorrection.QUARTILE, "#000000",
                "#ffffff", ModuleShape.SQUARE, FinderShape.SQUARE, logo(30)));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.FAIL);
        assertThat(result.trials()).noneMatch(ScanTrial::decoded);
        assertThat(result.hints().get(0)).contains("High error correction");
        assertThat(result.hints()).anySatisfy(hint -> assertThat(hint).contains("Make the logo smaller"));
    }

    @Test
    void diamondEyesAreMarginalAndBlameTheCorners() {
        ScanCheckResponse result = check(URL, style(600, 4, ErrorCorrection.QUARTILE, "#101820",
                "#ffffff", ModuleShape.SQUARE, FinderShape.DIAMOND, null));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.MARGINAL);
        assertThat(trial(result, "rendered").decoded()).isTrue();
        assertThat(result.hints()).anySatisfy(hint -> assertThat(hint).contains("corner patterns"));
    }

    @Test
    void faintModulesFailWithAContrastHint() {
        ScanCheckResponse result = check(URL, style(600, 4, ErrorCorrection.MEDIUM, "#bbbbbb",
                "#ffffff", ModuleShape.SQUARE, FinderShape.SQUARE, null));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.FAIL);
        assertThat(result.hints().get(0)).contains("close in brightness");
    }

    @Test
    void aThinQuietZoneIsCappedAtMarginalEvenWhenItReads() {
        ScanCheckResponse result = check(URL, style(600, 0, ErrorCorrection.MEDIUM, "#000000",
                "#ffffff", ModuleShape.SQUARE, FinderShape.SQUARE, null));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.MARGINAL);
        assertThat(result.hints()).singleElement()
                .satisfies(hint -> assertThat(hint).contains("quiet zone"));
    }

    @Test
    void anInvertedCodeReadsButIsCappedAtMarginal() {
        ScanCheckResponse result = check(URL, style(600, 4, ErrorCorrection.MEDIUM, "#ffffff",
                "#000000", ModuleShape.SQUARE, FinderShape.SQUARE, null));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.MARGINAL);
        assertThat(trial(result, "rendered").decoded()).isTrue();
        assertThat(result.hints()).singleElement()
                .satisfies(hint -> assertThat(hint).contains("lighter than the background"));
    }

    @Test
    void aDenseCodeAtASmallSizeSuggestsShorterContent() {
        ScanCheckResponse result = check("x".repeat(600), style(256, 4, ErrorCorrection.MEDIUM,
                "#000000", "#ffffff", ModuleShape.SQUARE, FinderShape.SQUARE, null));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.MARGINAL);
        assertThat(result.hints()).anySatisfy(hint -> assertThat(hint).contains("short link"));
    }

    @Test
    void aTransparentDarkOnLightCodePasses() {
        ScanCheckResponse result = check(URL, transparent("#101820", "#ffffff"));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.PASS);
        assertThat(result.trials()).allMatch(ScanTrial::decoded);
        assertThat(result.hints()).isEmpty();
    }

    @Test
    void aTransparentCodeIsJudgedOnItsDeclaredBackground() {
        // Light modules on a transparent background. Over white they would vanish; over the
        // dark background the design is meant for, they read, capped as any inverted code is.
        ScanCheckResponse result = check(URL, transparent("#f8fafc", "#0f172a"));
        ScanCheckResponse opaque = check(URL, new QrStyle(600, 4, ErrorCorrection.QUARTILE,
                "#f8fafc", null, "#0f172a", null, ModuleShape.SQUARE, FinderShape.SQUARE, null,
                null, null, null, false));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.MARGINAL);
        assertThat(trial(result, "rendered").decoded()).isTrue();
        assertThat(result.trials()).isEqualTo(opaque.trials());
        assertThat(result.hints().get(0)).contains("lighter than the background");
        assertThat(result.hints()).last().satisfies(hint -> assertThat(hint)
                .contains("transparent").contains("#0f172a"));
    }

    @Test
    void aFaintTransparentCodeStillFailsOnItsBackground() {
        // Grey on a mid-grey background: transparent pixels must not read as white paper.
        ScanCheckResponse result = check(URL, transparent("#666666", "#888888"));

        assertThat(result.verdict()).isEqualTo(ScanVerdict.FAIL);
        assertThat(result.hints().get(0)).contains("close in brightness");
    }

    private ScanCheckResponse check(String data, QrStyle style) {
        return service.check(new QrCodeRequest(data, style));
    }

    private static ScanTrial trial(ScanCheckResponse result, String name) {
        return result.trials().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    private static QrStyle transparent(String foreground, String background) {
        return new QrStyle(600, 4, ErrorCorrection.QUARTILE, foreground, null, background, null,
                ModuleShape.SQUARE, FinderShape.SQUARE, null, null, null, null, true);
    }

    private static QrStyle style(int size, int margin, ErrorCorrection level, String foreground,
            String background, ModuleShape moduleShape, FinderShape finderShape, QrLogo logo) {
        return new QrStyle(size, margin, level, foreground, null, background, null, moduleShape,
                finderShape, null, null, logo, null);
    }

    private static QrLogo logo(int sizePercent) throws IOException {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xcc, 0x22, 0x22));
            graphics.fillRect(0, 0, 40, 40);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        return new QrLogo(dataUrl, sizePercent, 8, "#ffffff", 25);
    }
}

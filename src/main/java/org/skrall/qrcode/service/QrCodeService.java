package org.skrall.qrcode.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.skrall.qrcode.QrEncodingException;
import org.skrall.qrcode.check.ScanChecker;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.model.ScanCheckResponse;
import org.skrall.qrcode.render.LogoImage;
import org.skrall.qrcode.render.PngQrRenderer;
import org.skrall.qrcode.render.RenderOptions;
import org.skrall.qrcode.render.SvgQrRenderer;
import org.springframework.stereotype.Service;

/**
 * Encodes a payload and hands the matrix to the renderer for the requested format.
 */
@Service
public class QrCodeService {

    private final PngQrRenderer pngRenderer;
    private final SvgQrRenderer svgRenderer;
    private final ScanChecker scanChecker;

    public QrCodeService(
            PngQrRenderer pngRenderer, SvgQrRenderer svgRenderer, ScanChecker scanChecker) {
        this.pngRenderer = pngRenderer;
        this.svgRenderer = svgRenderer;
        this.scanChecker = scanChecker;
    }

    public byte[] render(QrCodeRequest request, QrFormat format) {
        Prepared prepared = prepare(request);
        return switch (format) {
            case PNG -> pngRenderer.render(prepared.matrix(), prepared.options());
            case SVG -> svgRenderer.render(prepared.matrix(), prepared.options());
        };
    }

    /**
     * Renders the code and reads it back.
     *
     * <p>The PNG renderer's pixels are what get read. The SVG has the same geometry, so the
     * verdict holds for both formats.
     */
    public ScanCheckResponse check(QrCodeRequest request) {
        Prepared prepared = prepare(request);
        return scanChecker.check(
                pngRenderer.draw(prepared.matrix(), prepared.options()),
                request.data(),
                prepared.matrix(),
                prepared.options());
    }

    /**
     * Checks that a style could be rendered, without rendering it: images decode within their
     * limits, a gradient contrasts with the background, a frame's text can be set. What depends
     * on the payload, such as whether a frame leaves enough quiet zone for the code, is checked
     * when the code is rendered.
     */
    public void validate(QrStyle style) {
        RenderOptions.from(style, LogoImage.decode(style == null ? null : style.logo()));
    }

    /**
     * Decodes and checks a style once, for rendering many payloads with it.
     *
     * <p>The images are decoded and washed here rather than per payload, which is what makes a
     * batch with a background image affordable. It fails as {@link #validate} does.
     */
    public Styled styled(QrStyle style) {
        return new Styled(options(style));
    }

    /** A style ready to render, with its images decoded. */
    public final class Styled {

        private final RenderOptions options;

        private Styled(RenderOptions options) {
            this.options = options;
        }

        /**
         * Encodes the payload once and renders it in each format asked for.
         *
         * @throws QrEncodingException when the payload does not fit
         * @throws org.skrall.qrcode.FrameException when a frame leaves too little quiet zone
         */
        public Map<QrFormat, byte[]> render(String data, Iterable<QrFormat> formats) {
            Prepared prepared = prepare(data, options);
            Map<QrFormat, byte[]> images = new EnumMap<>(QrFormat.class);
            for (QrFormat format : formats) {
                images.put(format, switch (format) {
                    case PNG -> pngRenderer.render(prepared.matrix(), prepared.options());
                    case SVG -> svgRenderer.render(prepared.matrix(), prepared.options());
                });
            }
            return images;
        }
    }

    private record Prepared(BitMatrix matrix, RenderOptions options) {
    }

    private static RenderOptions options(QrStyle style) {
        return RenderOptions.from(style, LogoImage.decode(style == null ? null : style.logo()));
    }

    private static Prepared prepare(QrCodeRequest request) {
        return prepare(request.data(), options(request.style()));
    }

    private static Prepared prepare(String data, RenderOptions options) {
        BitMatrix matrix = encode(data, options.errorCorrection());
        if (options.frame() != null) {
            // Only now is the module size known, which is what the quiet zone is measured in.
            options.frame().requireQuietZone(matrix.getWidth(), options.size(), options.margin());
        }
        return new Prepared(matrix, options);
    }

    /**
     * Encodes to a matrix with no quiet zone.
     *
     * <p>The margin is applied by the renderers in module units, so the matrix here is the bare
     * symbol. Asking ZXing for one would bake its own quiet zone in at a fixed width, which then
     * could not be expressed in the caller's chosen margin.
     */
    private static BitMatrix encode(String data, ErrorCorrection errorCorrection) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, level(errorCorrection));
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.MARGIN, 0);

        try {
            return new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, hints);
        } catch (WriterException e) {
            throw new QrEncodingException(
                    "The content is too long for a QR code at " + errorCorrection
                            + " error correction. Shorten it or choose a lower level.",
                    e);
        }
    }

    private static ErrorCorrectionLevel level(ErrorCorrection errorCorrection) {
        return switch (errorCorrection) {
            case LOW -> ErrorCorrectionLevel.L;
            case MEDIUM -> ErrorCorrectionLevel.M;
            case QUARTILE -> ErrorCorrectionLevel.Q;
            case HIGH -> ErrorCorrectionLevel.H;
        };
    }
}

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
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.model.QrStyle;
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

    public QrCodeService(PngQrRenderer pngRenderer, SvgQrRenderer svgRenderer) {
        this.pngRenderer = pngRenderer;
        this.svgRenderer = svgRenderer;
    }

    public byte[] render(QrCodeRequest request, QrFormat format) {
        QrStyle style = request.style();
        LogoImage logo = LogoImage.decode(style == null ? null : style.logo());
        RenderOptions options = RenderOptions.from(style, logo);
        BitMatrix matrix = encode(request.data(), options.errorCorrection());

        return switch (format) {
            case PNG -> pngRenderer.render(matrix, options);
            case SVG -> svgRenderer.render(matrix, options);
        };
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

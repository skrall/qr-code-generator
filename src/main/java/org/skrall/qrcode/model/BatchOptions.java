package org.skrall.qrcode.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Locale;

/**
 * How every row of a batch is rendered.
 *
 * @param format   {@code png} (the default), {@code svg}, {@code pdf} or {@code both} (PNG and
 *                 SVG), in any case
 * @param style    the style, as the render API takes it; optional
 * @param designId a saved design to take the style from instead; it must be visible to the
 *                 caller. Sending both this and {@code style} is refused.
 * @param dpi      the print resolution, as the render API's {@code dpi} parameter; optional
 */
public record BatchOptions(
        @Pattern(regexp = "(?i)png|svg|pdf|both", message = "format must be png, svg, pdf or both")
        String format,

        @Valid
        QrStyle style,

        Long designId,

        @Min(value = Dpi.MIN, message = Dpi.RANGE_MESSAGE)
        @Max(value = Dpi.MAX, message = Dpi.RANGE_MESSAGE)
        Integer dpi) {

    public static final BatchOptions DEFAULTS = new BatchOptions(null, null, null, null);

    public BatchOptions(String format, QrStyle style, Long designId) {
        this(format, style, designId, null);
    }

    /** The formats each row is rendered in, PNG first. */
    public List<QrFormat> formats() {
        if (format == null || format.isBlank()) {
            return List.of(QrFormat.PNG);
        }
        return switch (format.strip().toLowerCase(Locale.ROOT)) {
            case "svg" -> List.of(QrFormat.SVG);
            case "pdf" -> List.of(QrFormat.PDF);
            case "both" -> List.of(QrFormat.PNG, QrFormat.SVG);
            default -> List.of(QrFormat.PNG);
        };
    }
}

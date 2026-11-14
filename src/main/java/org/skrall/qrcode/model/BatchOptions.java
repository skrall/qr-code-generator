package org.skrall.qrcode.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Locale;

/**
 * How every row of a batch is rendered.
 *
 * @param format   {@code png} (the default), {@code svg} or {@code both}, in any case
 * @param style    the style, as the render API takes it; optional
 * @param designId a saved design to take the style from instead; it must be visible to the
 *                 caller. Sending both this and {@code style} is refused.
 */
public record BatchOptions(
        @Pattern(regexp = "(?i)png|svg|both", message = "format must be png, svg or both")
        String format,

        @Valid
        QrStyle style,

        Long designId) {

    public static final BatchOptions DEFAULTS = new BatchOptions(null, null, null);

    /** The formats each row is rendered in, PNG first. */
    public List<QrFormat> formats() {
        if (format == null || format.isBlank()) {
            return List.of(QrFormat.PNG);
        }
        return switch (format.strip().toLowerCase(Locale.ROOT)) {
            case "svg" -> List.of(QrFormat.SVG);
            case "both" -> List.of(QrFormat.PNG, QrFormat.SVG);
            default -> List.of(QrFormat.PNG);
        };
    }
}

package org.skrall.qrcode.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * A two-colour gradient that replaces the flat foreground colour of the modules.
 *
 * <p>Both stops must contrast with the background, and both must sit on the same side of it:
 * a gradient from dark to light would pass through the background colour and erase part of the
 * code. The server checks this and rejects a gradient that fails.
 *
 * @param type    LINEAR or RADIAL, default LINEAR
 * @param start   the colour at the start of the line, or at the centre, as {@code #rrggbb}
 * @param end     the colour at the end of the line, or at the corners, as {@code #rrggbb}
 * @param angle   for LINEAR, the direction in degrees as in CSS: 0 points up, 90 right,
 *                180 (the default) down
 * @param finders whether the three corner patterns take the gradient too, default false; when
 *                false they keep {@code finderColor}
 */
public record QrGradient(
        GradientType type,

        @NotNull(message = "style.gradient.start is required")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "style.gradient.start must be a hex colour such as #1b2430")
        String start,

        @NotNull(message = "style.gradient.end is required")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "style.gradient.end must be a hex colour such as #3158c4")
        String end,

        @Min(value = 0, message = "style.gradient.angle must be between 0 and 360")
        @Max(value = 360, message = "style.gradient.angle must be between 0 and 360")
        Integer angle,

        Boolean finders) {
}

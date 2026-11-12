package org.skrall.qrcode.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A logo to place in the middle of the code.
 *
 * <p>The image travels as a base64 data URL rather than as an uploaded file. That keeps the
 * generator stateless: there is no upload directory to clean up, no id to leak, and a request
 * carries everything needed to reproduce its output.
 *
 * @param dataUrl        a {@code data:image/...;base64,...} URL, at most 3 MB of text
 * @param sizePercent    the logo's width as a percentage of the image width, default 20
 * @param paddingPercent the light plate's padding as a percentage of the logo width, default 8
 * @param background     the plate colour, default white
 * @param radiusPercent  the plate's corner radius as a percentage of the plate width, default 25
 */
public record QrLogo(
        @NotBlank(message = "logo.dataUrl must not be blank")
        @Size(max = 3_000_000, message = "logo.dataUrl must be at most 3 MB")
        String dataUrl,

        @Min(value = 5, message = "logo.sizePercent must be at least 5")
        @Max(value = 35, message = "logo.sizePercent must be at most 35")
        Integer sizePercent,

        @Min(value = 0, message = "logo.paddingPercent must not be negative")
        @Max(value = 30, message = "logo.paddingPercent must be at most 30")
        Integer paddingPercent,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "logo.background must be a hex colour such as #ffffff")
        String background,

        @Min(value = 0, message = "logo.radiusPercent must not be negative")
        @Max(value = 50, message = "logo.radiusPercent must be at most 50")
        Integer radiusPercent) {
}

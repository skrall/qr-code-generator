package org.skrall.qrcode.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An image drawn behind the code.
 *
 * <p>Like a logo, the image travels as a base64 data URL rather than as an uploaded file, so the
 * generator stays stateless and a request carries everything needed to reproduce its output.
 *
 * <p>The image is cropped to a square, composited over the background colour and washed so its
 * luminance keeps the modules readable. A busy or dark photograph therefore appears as a pale
 * backdrop instead of swallowing the code.
 *
 * @param dataUrl a {@code data:image/...;base64,...} URL, at most 3 MB of text
 */
public record QrBackgroundImage(
        @NotBlank(message = "style.backgroundImage.dataUrl must not be blank")
        @Size(max = 3_000_000, message = "style.backgroundImage.dataUrl must be at most 3 MB")
        String dataUrl) {
}

package org.skrall.qrcode.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An image used to fill the three finder patterns.
 *
 * <p>Like a logo, the image travels as a base64 data URL rather than as an uploaded file, so the
 * generator stays stateless and a request carries everything needed to reproduce its output.
 *
 * <p>The artwork is only used when {@link FinderShape#CUSTOM} is selected.
 *
 * @param dataUrl a {@code data:image/...;base64,...} URL, at most 3 MB of text
 */
public record QrFinderArtwork(
        @NotBlank(message = "style.finderArtwork.dataUrl must not be blank")
        @Size(max = 3_000_000, message = "style.finderArtwork.dataUrl must be at most 3 MB")
        String dataUrl) {
}

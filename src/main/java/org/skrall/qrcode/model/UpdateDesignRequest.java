package org.skrall.qrcode.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A request to rename, share or unshare a design, and optionally overwrite its style.
 *
 * @param name   the design's name, which may be unchanged
 * @param shared whether everyone sees it; absent keeps the current setting
 * @param style  a style to replace the saved one, or absent to keep it, so renaming or sharing
 *               does not have to send megabytes of images back
 */
public record UpdateDesignRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        Boolean shared,

        @Valid
        QrStyle style) {
}

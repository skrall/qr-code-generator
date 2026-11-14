package org.skrall.qrcode.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A request to save a style as a new design.
 *
 * @param name   what the design is called in the list
 * @param shared whether everyone who can sign in sees it; absent means private
 * @param style  the style to save, images included, exactly as the render API takes it
 */
public record CreateDesignRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        Boolean shared,

        @NotNull(message = "style must not be null")
        @Valid
        QrStyle style) {
}

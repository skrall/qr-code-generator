package org.skrall.qrcode.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A request to render one QR code.
 *
 * @param data  the payload to encode; the caller is responsible for composing it, which keeps
 *              the server independent of the difference between a URL, a vCard and a WiFi
 *              network
 * @param style how it should look, optional
 */
public record QrCodeRequest(
        @NotBlank(message = "data must not be blank")
        @Size(max = 8000, message = "data must be at most 8000 characters")
        String data,

        @Valid
        QrStyle style) {
}

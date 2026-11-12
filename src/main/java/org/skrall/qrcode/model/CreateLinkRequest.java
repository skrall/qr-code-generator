package org.skrall.qrcode.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * A request to store a destination and get a short link for it.
 *
 * @param url       the destination; must be an absolute http or https URL
 * @param expiresAt when the link stops working, or null for never
 */
public record CreateLinkRequest(
        @NotBlank(message = "url must not be blank")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,

        Instant expiresAt) {
}

package org.skrall.qrcode.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * A request to repoint an existing short link at a new destination.
 *
 * @param url         the new destination; must be an absolute http or https URL
 * @param expiresAt   when the link stops working, or null for never
 * @param goneUrl     where a scan goes once the link has expired or been deleted; null or blank
 *                    for the 410 page. Checked like {@code url}.
 * @param goneMessage plain text shown on the 410 page instead of the default wording; null or
 *                    blank for the default. Escaped when shown.
 */
public record UpdateLinkRequest(
        @NotBlank(message = "url must not be blank")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,

        Instant expiresAt,

        @Size(max = 2048, message = "goneUrl must be at most 2048 characters")
        String goneUrl,

        @Size(max = 280, message = "goneMessage must be at most 280 characters")
        String goneMessage) {
}

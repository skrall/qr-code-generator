package org.skrall.qrcode.link;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where an expired or deleted link sends a scan when the link has neither a fallback nor a
 * message of its own.
 *
 * <p>Unset by default, so such a code answers {@code 410 Gone} with a page. The value is checked
 * like a link's destination at startup, so a typo fails there rather than on a phone.
 *
 * @param goneUrl absolute http or https URL, or blank for none ({@code app.links.gone-url})
 */
@ConfigurationProperties(prefix = "app.links")
public record GoneProperties(String goneUrl) {

    public GoneProperties {
        if (goneUrl == null || goneUrl.isBlank()) {
            goneUrl = null;
        } else {
            try {
                goneUrl = ShortLinkService.validUrl("app.links.gone-url", goneUrl);
            } catch (InvalidLinkException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
    }
}

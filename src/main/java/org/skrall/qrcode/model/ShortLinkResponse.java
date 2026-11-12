package org.skrall.qrcode.model;

import java.time.Instant;
import org.skrall.qrcode.link.LinkProperties;
import org.skrall.qrcode.link.ShortLink;

/**
 * A stored link as the API returns it.
 *
 * <p>{@code shortUrl} is composed here rather than in the client so that the public base URL is
 * configured in one place. A caller never has to know how the service is deployed.
 */
public record ShortLinkResponse(
        String code,
        String shortUrl,
        String targetUrl,
        Instant createdAt,
        Instant expiresAt,
        long hitCount,
        Instant lastHitAt) {

    public static ShortLinkResponse from(ShortLink link, LinkProperties properties) {
        return new ShortLinkResponse(
                link.getCode(),
                properties.shortUrl(link.getCode()),
                link.getTargetUrl(),
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.getHitCount(),
                link.getLastHitAt());
    }
}

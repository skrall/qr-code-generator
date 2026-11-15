package org.skrall.qrcode.link;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where short links point, and how they are addressed.
 *
 * <p>The public base URL is the address a scanner must be able to reach, which is not the same
 * as the address the app listens on: behind a proxy it is the proxy's HTTPS name, and on a LAN
 * it is this machine's LAN address. Getting it wrong produces a code that works on the machine
 * that made it and nowhere else, so a bad value fails at startup rather than at scan time.
 *
 * @param publicBaseUrl absolute http or https URL, without a trailing slash
 */
@ConfigurationProperties(prefix = "app")
public record LinkProperties(String publicBaseUrl) {

    public LinkProperties {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalArgumentException("app.public-base-url must not be blank");
        }

        String normalized = publicBaseUrl.strip().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "app.public-base-url is not a valid URL: " + publicBaseUrl, e);
        }

        String scheme = uri.getScheme();
        boolean absoluteHttp = scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        if (!absoluteHttp || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "app.public-base-url must be an absolute http or https URL: " + publicBaseUrl);
        }

        publicBaseUrl = normalized;
    }

    /** The URL a code encodes. */
    public String shortUrl(String code) {
        return publicBaseUrl + "/r/" + code;
    }

    /**
     * Whether a URL addresses one of this service's short links, i.e. its redirect path under the
     * public base URL.
     *
     * <p>Used to keep a gone link's fallback from pointing at a short link: once that link expires
     * too, the two redirect to each other, or a link to itself, forever. The host is compared
     * without regard to case, and the scheme is ignored when both URLs use their scheme's
     * default port, because a proxy commonly answers the same name on http and https. Other
     * names for the same machine (a LAN address and {@code localhost}, say) cannot be recognised.
     */
    public boolean isShortLink(String url) {
        URI candidate;
        try {
            candidate = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        URI base = URI.create(publicBaseUrl);
        if (candidate.getHost() == null || !candidate.getHost().equalsIgnoreCase(base.getHost())) {
            return false;
        }
        int candidatePort = effectivePort(candidate);
        int basePort = effectivePort(base);
        boolean bothDefault = candidatePort == defaultPort(candidate) && basePort == defaultPort(base);
        if (candidatePort != basePort && !bothDefault) {
            return false;
        }
        String basePath = base.getRawPath() == null ? "" : base.getRawPath();
        String path = candidate.getRawPath() == null ? "" : candidate.getRawPath();
        return path.startsWith(basePath + "/r/");
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() == -1 ? defaultPort(uri) : uri.getPort();
    }

    private static int defaultPort(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}

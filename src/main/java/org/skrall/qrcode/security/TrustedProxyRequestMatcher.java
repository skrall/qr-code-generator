package org.skrall.qrcode.security;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Matches a request whose connection really came from a configured proxy.
 *
 * <p>The identity header is only believed on such requests, so the match has to be against the
 * transport peer, not the {@code X-Forwarded-For} client: with {@code server.forward-headers-
 * strategy: framework} in front, {@code getRemoteAddr()} is already the client's address by the
 * time security runs, and an attacker controlling that header would otherwise be able to name a
 * trusted address. The innermost wrapped request is the one the socket arrived on; unwinding
 * Spring's forwarded-header wrapper to it recovers the actual peer.
 *
 * <p>Fail-fast on a CIDR that does not parse: a mistyped subnet that silently matched nothing
 * would lock everyone out, and one that silently matched everything would trust everyone.
 */
public final class TrustedProxyRequestMatcher implements RequestMatcher {

    private final List<IpAddressMatcher> matchers;

    public TrustedProxyRequestMatcher(List<String> cidrs) {
        List<IpAddressMatcher> built = new ArrayList<>();
        for (String cidr : cidrs) {
            try {
                built.add(new IpAddressMatcher(cidr));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "app.security.trusted-proxies contains an invalid CIDR: "
                                + cidr, e);
            }
        }
        this.matchers = List.copyOf(built);
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        String peer = peerAddress(request);
        if (peer == null || peer.isBlank()) {
            return false;
        }
        for (IpAddressMatcher matcher : matchers) {
            try {
                if (matcher.matches(peer)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // A peer that does not parse as an IP is not a proxy that vouched for anyone.
            }
        }
        return false;
    }

    /**
     * The address of the socket the request arrived on, whichever servlet filter has since
     * wrapped the request. Mirrors a plain request (no forwarded-header strategy, or tests) to
     * {@code getRemoteAddr()} unchanged.
     */
    private static String peerAddress(HttpServletRequest request) {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper) {
            ServletRequest nested = wrapper.getRequest();
            if (nested == null) {
                break;
            }
            current = nested;
        }
        return current.getRemoteAddr();
    }
}
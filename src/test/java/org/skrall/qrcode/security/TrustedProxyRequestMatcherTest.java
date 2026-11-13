package org.skrall.qrcode.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The matcher answers "did this connection come from a proxy I trust?" from the address of the
 * transport peer, not from any header — a header can be forged, the four bytes of the TCP peer
 * cannot (short of breaking the network itself).
 */
class TrustedProxyRequestMatcherTest {

    private final TrustedProxyRequestMatcher matcher =
            new TrustedProxyRequestMatcher(List.of("10.0.0.0/8", "127.0.0.1/32", "::1/128"));

    private static MockHttpServletRequest from(String remoteAddr) {
        return new MockHttpServletRequest("GET", "/messages/list") {{
            setRemoteAddr(remoteAddr);
        }};
    }

    @Test
    void anAddressInsideACidr_isTrusted() {
        assertThat(matcher.matches(from("10.0.0.6"))).isTrue();
    }

    @Test
    void theLoopbackCidr_isTrusted() {
        assertThat(matcher.matches(from("127.0.0.1"))).isTrue();
    }

    @Test
    void anAddressOutsideTheCidrs_isNotTrusted() {
        assertThat(matcher.matches(from("192.168.1.4"))).isFalse();
    }

    @Test
    void anIPv6Cidr_matches() {
        assertThat(matcher.matches(from("::1"))).isTrue();
    }

    @Test
    void aMalformedAddress_isNotTrusted() {
        assertThat(matcher.matches(from("not-an-address"))).isFalse();
    }

    @Test
    void aMalformedCidr_isRefusedAtConstruction() {
        assertThatThrownBy(() -> new TrustedProxyRequestMatcher(List.of("not-a-cidr")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-cidr");
    }
}
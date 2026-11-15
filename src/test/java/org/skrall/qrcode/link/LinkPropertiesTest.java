package org.skrall.qrcode.link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkPropertiesTest {

    private final LinkProperties local = new LinkProperties("http://localhost:8080");
    private final LinkProperties proxied = new LinkProperties("https://qr.example.com/");
    private final LinkProperties subPath = new LinkProperties("https://example.com/tools/qr");

    @Test
    void recognisesItsOwnShortLinks() {
        assertThat(local.isShortLink(local.shortUrl("abc123XYZ"))).isTrue();
        assertThat(local.isShortLink("http://LocalHost:8080/r/other")).isTrue();
        assertThat(proxied.isShortLink("https://qr.example.com/r/abc")).isTrue();
        assertThat(subPath.isShortLink("https://example.com/tools/qr/r/abc")).isTrue();
    }

    @Test
    void treatsHttpAndHttpsOnDefaultPortsAsTheSameName() {
        // A proxy commonly answers both, so either would come back to this service.
        assertThat(proxied.isShortLink("http://qr.example.com/r/abc")).isTrue();
        assertThat(proxied.isShortLink("https://qr.example.com:443/r/abc")).isTrue();
    }

    @Test
    void doesNotClaimOtherAddresses() {
        assertThat(local.isShortLink("http://localhost:9090/r/abc")).as("another port").isFalse();
        assertThat(local.isShortLink("https://localhost/r/abc")).as("default port, base is 8080").isFalse();
        assertThat(local.isShortLink("http://example.com/r/abc")).as("another host").isFalse();
        assertThat(local.isShortLink("http://localhost:8080/")).as("not the redirect path").isFalse();
        assertThat(local.isShortLink("http://localhost:8080/rr/abc")).isFalse();
        assertThat(subPath.isShortLink("https://example.com/r/abc")).as("outside the base path").isFalse();
        assertThat(local.isShortLink("not a url")).isFalse();
    }
}

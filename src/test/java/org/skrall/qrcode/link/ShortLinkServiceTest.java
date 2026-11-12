package org.skrall.qrcode.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ShortLinkServiceTest {

    @Test
    void acceptsAbsoluteHttpAndHttpsUrls() {
        assertThat(ShortLinkService.targetUrl("https://example.com/a?b=c#d"))
                .isEqualTo("https://example.com/a?b=c#d");
        assertThat(ShortLinkService.targetUrl("http://example.com")).isEqualTo("http://example.com");
        assertThat(ShortLinkService.targetUrl("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @Test
    void percentEncodesANonAsciiPathSoTheLocationHeaderStaysAscii() {
        assertThat(ShortLinkService.targetUrl("https://example.com/caf\u00e9"))
                .isEqualTo("https://example.com/caf%C3%A9");
    }

    @Test
    void rejectsSchemesThatWouldExecuteInTheBrowser() {
        assertThatThrownBy(() -> ShortLinkService.targetUrl("javascript:alert(1)"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("http or https");
        assertThatThrownBy(() -> ShortLinkService.targetUrl("data:text/html,<script>alert(1)</script>"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("http or https");
        assertThatThrownBy(() -> ShortLinkService.targetUrl("file:///etc/passwd"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void rejectsRelativeUrlsAndMissingHosts() {
        assertThatThrownBy(() -> ShortLinkService.targetUrl("example.com"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("absolute");
        assertThatThrownBy(() -> ShortLinkService.targetUrl("https://"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("valid absolute URL");
    }

    @Test
    void rejectsCredentialsInTheUrl() {
        assertThatThrownBy(() -> ShortLinkService.targetUrl("https://user:secret@example.com"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("credentials");
    }

    @Test
    void rejectsBlankAndOversizedDestinations() {
        assertThatThrownBy(() -> ShortLinkService.targetUrl("   "))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> ShortLinkService.targetUrl("https://example.com/" + "a".repeat(2100)))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("2048");
    }
}

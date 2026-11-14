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

    @Test
    void aFallbackIsCheckedLikeADestination() {
        assertThat(ShortLinkService.goneUrl(null)).isNull();
        assertThat(ShortLinkService.goneUrl("   ")).isNull();
        assertThat(ShortLinkService.goneUrl(" https://example.com/caf\u00e9 "))
                .isEqualTo("https://example.com/caf%C3%A9");

        assertThatThrownBy(() -> ShortLinkService.goneUrl("javascript:alert(1)"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessage("goneUrl must be an absolute http or https URL");
        assertThatThrownBy(() -> ShortLinkService.goneUrl("data:text/html,<script>alert(1)</script>"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("http or https");
        assertThatThrownBy(() -> ShortLinkService.goneUrl("https://user:pw@example.com"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessage("goneUrl must not contain credentials");
        assertThatThrownBy(() -> ShortLinkService.goneUrl("https://example.com/" + "a".repeat(2100)))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessage("goneUrl must be at most 2048 characters");
    }

    @Test
    void aMessageIsTrimmedAndKeepsItsLineBreaks() {
        assertThat(ShortLinkService.goneMessage(null)).isNull();
        assertThat(ShortLinkService.goneMessage(" \n ")).isNull();
        assertThat(ShortLinkService.goneMessage("  The sale is over.\r\nSee you next year!  "))
                .isEqualTo("The sale is over.\nSee you next year!");
        assertThat(ShortLinkService.goneMessage("<b>markup</b> is kept as text"))
                .isEqualTo("<b>markup</b> is kept as text");
    }

    @Test
    void aMessageIsLengthLimitedAndHasNoControlCharacters() {
        assertThat(ShortLinkService.goneMessage("a".repeat(280))).hasSize(280);
        assertThatThrownBy(() -> ShortLinkService.goneMessage("a".repeat(281)))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessage("goneMessage must be at most 280 characters");
        assertThatThrownBy(() -> ShortLinkService.goneMessage("bell\u0007"))
                .isInstanceOf(InvalidLinkException.class)
                .hasMessageContaining("control characters");
    }

    @Test
    void theConfiguredDefaultIsValidatedAtStartup() {
        assertThat(new GoneProperties(null).goneUrl()).isNull();
        assertThat(new GoneProperties(" ").goneUrl()).isNull();
        assertThat(new GoneProperties("https://example.com/gone").goneUrl())
                .isEqualTo("https://example.com/gone");
        assertThatThrownBy(() -> new GoneProperties("ftp://example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.links.gone-url");
    }
}

package org.skrall.qrcode.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LinkCodeGeneratorTest {

    private final LinkCodeGenerator generator = new LinkCodeGenerator();

    @Test
    void makesBase62CodesOfTheExpectedLength() {
        assertThat(generator.next()).hasSize(LinkCodeGenerator.LENGTH).matches("[0-9A-Za-z]+");
    }

    @Test
    void doesNotRepeatItself() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            seen.add(generator.next());
        }
        assertThat(seen).hasSize(5000);
    }

    @Test
    void shortUrlIsBuiltFromTheConfiguredBase() {
        LinkProperties properties = new LinkProperties("https://qr.example.com/");

        assertThat(properties.shortUrl("abc123XYZ")).isEqualTo("https://qr.example.com/r/abc123XYZ");
    }

    @Test
    void rejectsABaseUrlThatAPhoneCouldNotReach() {
        assertThatThrownBy(() -> new LinkProperties("ftp://example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http or https");
        assertThatThrownBy(() -> new LinkProperties("example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
        assertThatThrownBy(() -> new LinkProperties("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }
}

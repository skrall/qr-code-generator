package org.skrall.qrcode.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FontConfigFallbackTest {

    @Test
    void isOnlyNeededOnLinuxWithoutAnyFontConfiguration(@TempDir Path directory) throws Exception {
        Path missing = directory.resolve("fonts.conf");
        Path present = Files.writeString(directory.resolve("present.conf"), "<fontconfig/>");

        assertThat(FontConfigFallback.needed("Linux", null, missing)).isTrue();
        assertThat(FontConfigFallback.needed("Linux", null, present)).isFalse();
        assertThat(FontConfigFallback.needed("Linux", "/custom.properties", missing)).isFalse();
        assertThat(FontConfigFallback.needed("Mac OS X", null, missing)).isFalse();
        assertThat(FontConfigFallback.needed("Windows 11", null, missing)).isFalse();
    }

    @Test
    void mapsEveryLogicalFontToTheBundledFont(@TempDir Path directory) throws Exception {
        Path configuration = FontConfigFallback.write(directory);
        String properties = Files.readString(configuration);

        Path font = directory.resolve("Inter-Regular.ttf");
        assertThat(properties).contains("version=1", "sequence.allfonts=latin-1",
                "filename.inter=" + font);
        for (String family : new String[] {"serif", "sansserif", "monospaced", "dialog", "dialoginput"}) {
            for (String style : new String[] {"plain", "bold", "italic", "bolditalic"}) {
                assertThat(properties).contains(family + "." + style + ".latin-1=inter");
            }
        }
        // The file it names must be the real font, not just a path.
        assertThat(Font.createFont(Font.TRUETYPE_FONT, font.toFile()).getFamily()).isEqualTo("Inter");
    }
}

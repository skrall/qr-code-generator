package org.skrall.qrcode.render;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * The fonts a frame's text is set in, bundled rather than taken from the system.
 *
 * <p>A server's installed fonts vary, and a headless container may have none, so the same request
 * could render differently from one host to the next. The SVG renderer does not rely on the
 * viewer's fonts either: both renderers fill the glyph outlines of these files, so the two
 * formats show the same letters. Inter is under the SIL Open Font License; see
 * {@code fonts/Inter-LICENSE.txt}.
 */
final class FrameFonts {

    /** The title's face. */
    static final Font BOLD = load("/fonts/Inter-Bold.ttf");

    /** The caption's face. */
    static final Font REGULAR = load("/fonts/Inter-Regular.ttf");

    private FrameFonts() {
    }

    private static Font load(String resource) {
        try (InputStream in = FrameFonts.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("The bundled font " + resource + " is missing.");
            }
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(Map.of(
                    TextAttribute.KERNING, TextAttribute.KERNING_ON,
                    TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON));
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("The bundled font " + resource + " could not be read.", e);
        }
    }
}

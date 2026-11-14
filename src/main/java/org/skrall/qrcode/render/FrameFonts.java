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

    static {
        // Must run before the first font loads: a distroless host has no font configuration,
        // and without one the JDK refuses to load any font at all.
        FontConfigFallback.install();
    }

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
            // When the font manager itself cannot start, that is the cause worth reporting: it is
            // what a log's "root cause" line shows, and the createFont message names nothing.
            Throwable cause = fontManagerFailure().orElse(e);
            IllegalStateException failure =
                    new IllegalStateException("The bundled font " + resource + " could not be read.", cause);
            if (cause != e) {
                failure.addSuppressed(e);
            }
            throw failure;
        }
    }

    /**
     * Why the JDK's font manager will not start, if that is the problem.
     *
     * <p>{@code Font.createFont} reports every failure as "Problem reading font data", including a
     * font manager that cannot start at all (no font configuration, a native library that will
     * not load). Asking a logical font for its family starts the same font manager but lets the
     * real error through, so it becomes the cause rather than being lost.
     */
    private static java.util.Optional<Throwable> fontManagerFailure() {
        try {
            new Font(Font.DIALOG, Font.PLAIN, 12).getFamily();
            return java.util.Optional.empty();
        } catch (Throwable t) {
            return java.util.Optional.of(t);
        }
    }
}

package org.skrall.qrcode.nativeimage;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * The bundled frame font. {@code FrameFonts} reads it with {@code getResourceAsStream}, which a
 * native image only answers for resources registered at build time; anything else comes back
 * {@code null}, and every framed code would fail.
 */
public class FontRuntimeHints implements RuntimeHintsRegistrar {

    static final String FONT_PATTERN = "fonts/*.ttf";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern(FONT_PATTERN);
    }
}

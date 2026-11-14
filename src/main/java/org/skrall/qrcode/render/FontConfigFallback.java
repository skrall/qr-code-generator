package org.skrall.qrcode.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lets Java2D start on a Linux host that has no fonts and no fontconfig, such as the distroless
 * image {@code spring-boot:build-image} produces.
 *
 * <p>The JDK's font manager insists on a system font configuration before it loads any font,
 * even one the application supplies itself. Without one, the first framed code fails:
 * <pre>
 * java.io.IOException: Problem reading font data.
 * Caused by: java.lang.RuntimeException: Fontconfig head is null, check your fonts or fonts
 *     configuration
 * </pre>
 * The JDK's documented escape hatch is {@code sun.awt.fontconfig}: a {@code fontconfig.properties}
 * file mapping the logical fonts to font files. This writes one that maps every logical font to
 * the bundled Inter, copied to a temporary directory, and points the property at it.
 *
 * <p>It only acts when it is needed: on Linux, with no {@code /etc/fonts/fonts.conf}, and with the
 * property not already set. A desktop or a CI runner keeps its own font configuration. The
 * application never draws text in a logical font, so mapping them all to Inter changes nothing
 * it renders.
 */
final class FontConfigFallback {

    static final String PROPERTY = "sun.awt.fontconfig";

    private static final String FONT_RESOURCE = "/fonts/Inter-Regular.ttf";
    private static final Path SYSTEM_CONFIG = Path.of("/etc/fonts/fonts.conf");
    private static final List<String> FAMILIES =
            List.of("serif", "sansserif", "monospaced", "dialog", "dialoginput");
    private static final List<String> STYLES = List.of("plain", "bold", "italic", "bolditalic");

    private static boolean installed;

    private FontConfigFallback() {
    }

    /** Installs the fallback if this host needs it. Safe to call more than once. */
    static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        if (!needed(System.getProperty("os.name", ""), System.getProperty(PROPERTY), SYSTEM_CONFIG)) {
            return;
        }
        try {
            Path directory = Files.createTempDirectory("qr-code-fonts");
            Path configuration = write(directory);
            System.setProperty(PROPERTY, configuration.toString());
        } catch (IOException e) {
            // Leave the JDK to its own devices: plain codes still render, and a frame will fail
            // with the JDK's own message, which names the real problem.
            throw new UncheckedIOException("Could not write a fallback font configuration.", e);
        }
    }

    static boolean needed(String osName, String existingProperty, Path systemConfig) {
        return osName.toLowerCase(Locale.ROOT).contains("linux")
                && existingProperty == null
                && !Files.exists(systemConfig);
    }

    /** Copies the font into {@code directory} and writes the configuration beside it. */
    static Path write(Path directory) throws IOException {
        Path font = directory.resolve("Inter-Regular.ttf");
        try (InputStream in = FontConfigFallback.class.getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IOException("The bundled font " + FONT_RESOURCE + " is missing.");
            }
            Files.copy(in, font, StandardCopyOption.REPLACE_EXISTING);
        }

        String mappings = FAMILIES.stream()
                .flatMap(family -> STYLES.stream().map(style -> family + "." + style + ".latin-1=inter"))
                .collect(Collectors.joining("\n"));
        String properties = Stream.of(
                        "# Written by the QR code generator because this host has no fontconfig.",
                        "version=1",
                        "sequence.allfonts=latin-1",
                        mappings,
                        // A properties value: backslashes would need escaping, which a Linux
                        // temp path does not contain.
                        "filename.inter=" + font)
                .collect(Collectors.joining("\n", "", "\n"));

        Path configuration = directory.resolve("fontconfig.properties");
        Files.writeString(configuration, properties);
        font.toFile().deleteOnExit();
        configuration.toFile().deleteOnExit();
        directory.toFile().deleteOnExit();
        return configuration;
    }
}

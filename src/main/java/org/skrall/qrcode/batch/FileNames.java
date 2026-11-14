package org.skrall.qrcode.batch;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns the {@code filename} column into ZIP entry names that are safe to extract anywhere.
 *
 * <p>A name keeps letters, digits, {@code .}, {@code -} and {@code _}; anything else, path
 * separators included, becomes {@code _}. Runs of dots collapse to one, so {@code ..} cannot
 * survive, and leading or trailing dots, dashes and underscores go, so no name is hidden or
 * empty-looking. Names are cut to {@link #MAX_LENGTH} characters, a trailing {@code .png} or
 * {@code .svg} is dropped because the extension is added per format, and a Windows device name
 * such as {@code CON} gets a {@code _} appended. Names are unique without regard to case, since
 * the archive may be extracted on a case-insensitive file system.
 *
 * <p>Not thread-safe: one instance per batch.
 */
public final class FileNames {

    public static final int MAX_LENGTH = 80;

    private static final Pattern UNSAFE = Pattern.compile("[^\\p{L}\\p{N}._-]+");
    private static final Pattern DOTS = Pattern.compile("\\.{2,}");
    private static final Pattern UNDERSCORES = Pattern.compile("_{2,}");
    private static final Pattern EDGES = Pattern.compile("^[._-]+|[._-]+$");
    private static final Pattern IMAGE_EXTENSION = Pattern.compile("(?i)\\.(png|svg)$");
    private static final Pattern DEVICE = Pattern.compile("(?i)(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?");

    private final Set<String> taken = new HashSet<>();

    /**
     * The entry name, without extension, for a row: its sanitised {@code filename}, or
     * {@code row-<n>} when that is absent or sanitises to nothing, made unique with {@code -2},
     * {@code -3} and so on.
     */
    public String assign(String requested, int row) {
        String base = sanitise(requested);
        if (base == null) {
            base = "row-" + row;
        }
        String name = base;
        for (int n = 2; !taken.add(name.toLowerCase(Locale.ROOT)); n++) {
            name = base + "-" + n;
        }
        return name;
    }

    /** The safe form of a requested name, or {@code null} when nothing usable is left. */
    static String sanitise(String requested) {
        if (requested == null) {
            return null;
        }
        String name = Normalizer.normalize(requested.strip(), Normalizer.Form.NFC);
        name = IMAGE_EXTENSION.matcher(name).replaceFirst("");
        name = UNSAFE.matcher(name).replaceAll("_");
        name = DOTS.matcher(name).replaceAll(".");
        name = UNDERSCORES.matcher(name).replaceAll("_");
        name = EDGES.matcher(name).replaceAll("");
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
            name = name.substring(0, name.offsetByCodePoints(0, MAX_LENGTH));
            name = EDGES.matcher(name).replaceAll("");
        }
        if (name.isEmpty()) {
            return null;
        }
        return DEVICE.matcher(name).matches() ? name + "_" : name;
    }
}

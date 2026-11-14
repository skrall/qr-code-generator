package org.skrall.qrcode.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Mode;
import com.google.zxing.qrcode.decoder.Version;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.QrEncodingException;
import org.skrall.qrcode.check.ScanChecker;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.render.PngQrRenderer;
import org.skrall.qrcode.render.SvgQrRenderer;

/**
 * Pins the UI's size readout ({@code ui/src/qr/capacity.ts}) to ZXing.
 *
 * <p>The fixture lists payloads and the version ZXing picks for each at every error-correction
 * level, with the hints {@link QrCodeService} encodes with. {@code capacity.spec.ts} replays it
 * through the TypeScript module. This test regenerates the fixture and fails if the committed
 * copy differs, so a ZXing upgrade or a new case cannot leave the two sides disagreeing.
 *
 * <p>Regenerate with {@code ./mvnw test -Dskip.ui=true -Dtest=CapacityParityTest
 * -Dcapacity.fixture.write=true}.
 */
class CapacityParityTest {

    static final Path FIXTURE = Path.of("ui/src/qr/capacity.fixture.json");
    static final String WRITE_PROPERTY = "capacity.fixture.write";

    private static final ErrorCorrection[] LEVELS = ErrorCorrection.values();

    /** Versions whose upper edge is pinned: the count-width changes (9/10, 26/27) and the end. */
    private static final int[] EDGES = {1, 2, 5, 9, 10, 26, 27, 39, 40};

    /** One unit per mode and per UTF-8 width, including Java's 1-byte `?` for a lone surrogate. */
    private static final String[][] UNITS = {
        {"digits", "", "7"},
        {"alphanumeric", "", "A"},
        {"ASCII URL", "https://example.com/", "a"},
        {"2-byte", "", "é"},
        {"3-byte", "", "中"},
        {"4-byte", "", "😀"},
        {"lone surrogate", "", "\uD800"},
    };

    private static final String[][] LITERALS = {
        {"single space", " "},
        {"single digit", "0"},
        {"single upper-case letter", "A"},
        {"single lower-case letter", "a"},
        {"URL", "https://example.com"},
        {"URL with query", "https://example.com/path?q=1&x=Y#top"},
        {"upper-case URL", "HTTPS://EXAMPLE.COM/ABC"},
        {"every alphanumeric symbol", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"},
        {"digits with a space", "12 34"},
        {"digits and letters", "ABC123"},
        {"ten digits", "1234567890"},
        {"twenty digits", "01234567890123456789"},
        {"punctuation", "Hello, World!"},
        {"newline", "line one\nline two"},
        {"tab", "a\tb"},
        {"accents", "café naïve façade"},
        {"Cyrillic", "Привет, мир"},
        {"CJK", "日本語のテキスト"},
        {"emoji", "👍 emoji 🎉"},
        {"ZWJ family", "👨‍👩‍👧‍👦"},
        {"lone high surrogate", "a\uD800b"},
        {"lone low surrogate", "\uDC00"},
        {"WiFi", "WIFI:T:WPA;S:Home Network;P:correct horse;;"},
        {"mailto", "mailto:someone@example.com?subject=Hello"},
        {"vCard", "BEGIN:VCARD\nVERSION:3.0\nN:Doe;Jane\nFN:Jane Doe\nTEL:+15551234567\nEMAIL:jane@example.com\nEND:VCARD"},
        {"geo", "geo:52.3676,4.9041"},
    };

    private final QrCodeService service =
            new QrCodeService(new PngQrRenderer(), new SvgQrRenderer(), new ScanChecker());

    @Test
    void theCommittedFixtureMatchesZxing() throws IOException, WriterException {
        String generated = fixture();
        if (Boolean.getBoolean(WRITE_PROPERTY)) {
            Files.writeString(FIXTURE, generated, StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(FIXTURE, StandardCharsets.UTF_8))
                .as("%s is stale; regenerate it with -D%s=true and commit it", FIXTURE, WRITE_PROPERTY)
                .isEqualTo(generated);
    }

    /** The fixture uses this test's hints; this ties them to what the service really renders. */
    @Test
    void theServiceEncodesAtTheVersionTheFixtureRecords() throws WriterException {
        for (String payload : List.of("https://example.com", "0123456789", "日本語", "7".repeat(1000))) {
            for (ErrorCorrection level : LEVELS) {
                assertThat(service.check(request(payload, level)).version())
                        .as("%s at %s", payload, level)
                        .isEqualTo(version(payload, level));
            }
        }
        // Version 40-M holds 2334 codewords; the ECI, mode and count headers take 4 of them.
        assertThat(version("a".repeat(2330), ErrorCorrection.MEDIUM)).isEqualTo(40);
        service.check(request("a".repeat(2330), ErrorCorrection.MEDIUM));
        assertThat(version("a".repeat(2331), ErrorCorrection.MEDIUM)).isNull();
        assertThatThrownBy(() -> service.render(request("a".repeat(2331), ErrorCorrection.MEDIUM),
                QrFormat.PNG))
                .isInstanceOf(QrEncodingException.class);
    }

    private static QrCodeRequest request(String data, ErrorCorrection level) {
        return new QrCodeRequest(data,
                new QrStyle(256, 4, level, null, null, null, null, null, null, null, null, null, null));
    }

    /** The same hints as {@code QrCodeService.encode}. */
    private static Map<EncodeHintType, Object> hints(ErrorCorrection level) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, zxingLevel(level));
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.MARGIN, 0);
        return hints;
    }

    private static ErrorCorrectionLevel zxingLevel(ErrorCorrection level) {
        return switch (level) {
            case LOW -> ErrorCorrectionLevel.L;
            case MEDIUM -> ErrorCorrectionLevel.M;
            case QUARTILE -> ErrorCorrectionLevel.Q;
            case HIGH -> ErrorCorrectionLevel.H;
        };
    }

    /** The version ZXing encodes at, or null when it refuses the payload as too long. */
    private static Integer version(String payload, ErrorCorrection level) throws WriterException {
        QRCode code;
        try {
            code = Encoder.encode(payload, zxingLevel(level), hints(level));
        } catch (WriterException e) {
            return null;
        }
        Mode chosen = Encoder.chooseMode(payload);
        if (code.getMode() != chosen) {
            throw new WriterException("mode " + code.getMode() + " differs from chooseMode " + chosen);
        }
        return code.getVersion().getVersionNumber();
    }

    /**
     * Searching uses a fixed mask, which skips the mask scoring and does not change the version;
     * the recorded results are then encoded again with the service's hints.
     */
    private static int searchVersion(String payload, ErrorCorrection level) {
        Map<EncodeHintType, Object> hints = hints(level);
        hints.put(EncodeHintType.QR_MASK_PATTERN, 0);
        try {
            return Encoder.encode(payload, zxingLevel(level), hints).getVersion().getVersionNumber();
        } catch (WriterException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** The most repetitions of the unit that still encode at {@code edge} or below. */
    private static int lastTimesAtOrBelow(String prefix, String unit, ErrorCorrection level, int edge) {
        int low = 0;
        int high = 8000;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            String payload = prefix + unit.repeat(middle);
            if (!payload.isEmpty() && searchVersion(payload, level) <= edge) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private record Case(String name, String prefix, String repeat, int times) {
        String payload() {
            return prefix + repeat.repeat(times);
        }
    }

    private static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        for (String[] literal : LITERALS) {
            cases.add(new Case(literal[0], literal[1], "", 0));
        }
        for (String[] unit : UNITS) {
            Set<Integer> times = new TreeSet<>();
            for (ErrorCorrection level : LEVELS) {
                for (int edge : EDGES) {
                    int last = lastTimesAtOrBelow(unit[1], unit[2], level, edge);
                    if (last > 0) {
                        times.add(last);
                    }
                    times.add(last + 1);
                }
            }
            for (int count : times) {
                cases.add(new Case(unit[0] + " x" + count, unit[1], unit[2], count));
            }
        }
        return cases;
    }

    static String fixture() throws WriterException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"about\": ").append(quote(
                "Generated by CapacityParityTest from ZXing with QrCodeService's hints; do not edit. "
                        + "payload = prefix + repeat x times. versions: the version per level, "
                        + "null when ZXing rejects the payload as too long.")).append(",\n");

        json.append("  \"dataCodewords\": {\n");
        for (int i = 0; i < LEVELS.length; i++) {
            ErrorCorrectionLevel level = zxingLevel(LEVELS[i]);
            List<String> codewords = new ArrayList<>();
            for (int number = 1; number <= 40; number++) {
                Version version = Version.getVersionForNumber(number);
                codewords.add(String.valueOf(
                        version.getTotalCodewords() - version.getECBlocksForLevel(level).getTotalECCodewords()));
            }
            json.append("    ").append(quote(LEVELS[i].name())).append(": [")
                    .append(String.join(", ", codewords)).append(']')
                    .append(i < LEVELS.length - 1 ? ",\n" : "\n");
        }
        json.append("  },\n");

        json.append("  \"cases\": [\n");
        List<Case> cases = cases();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < cases.size(); i++) {
            Case item = cases.get(i);
            if (!seen.add(item.name())) {
                throw new IllegalStateException("duplicate case " + item.name());
            }
            String payload = item.payload();
            Map<String, String> versions = new LinkedHashMap<>();
            for (ErrorCorrection level : LEVELS) {
                Integer version = version(payload, level);
                versions.put(level.name(), version == null ? "null" : version.toString());
            }
            json.append("    {\"name\": ").append(quote(item.name()))
                    .append(", \"prefix\": ").append(quote(item.prefix()))
                    .append(", \"repeat\": ").append(quote(item.repeat()))
                    .append(", \"times\": ").append(item.times())
                    .append(", \"mode\": ").append(quote(Encoder.chooseMode(payload).name()))
                    .append(", \"bytes\": ").append(payload.getBytes(StandardCharsets.UTF_8).length)
                    .append(", \"versions\": {");
            List<String> entries = new ArrayList<>();
            versions.forEach((level, version) -> entries.add(quote(level) + ": " + version));
            json.append(String.join(", ", entries)).append('}').append('}')
                    .append(i < cases.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /** A JSON string with everything outside printable ASCII escaped, lone surrogates included. */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20 || c > 0x7e) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }
}

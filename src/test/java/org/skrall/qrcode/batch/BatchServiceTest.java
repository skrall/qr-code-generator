package org.skrall.qrcode.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.GradientException;
import org.skrall.qrcode.check.ScanChecker;
import org.skrall.qrcode.model.BatchOptions;
import org.skrall.qrcode.model.ErrorCorrection;
import org.skrall.qrcode.model.QrGradient;
import org.skrall.qrcode.model.GradientType;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.render.PngQrRenderer;
import org.skrall.qrcode.render.SvgQrRenderer;
import org.skrall.qrcode.security.Caller;
import org.skrall.qrcode.service.QrCodeService;

class BatchServiceTest {

    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Caller CALLER = new Caller("", null, true);

    /** No design is resolved here; the controller tests cover that path. */
    private final BatchService service = new BatchService(
            new QrCodeService(new PngQrRenderer(), new SvgQrRenderer(), new ScanChecker()), null, validator());

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    private static Validator validator() {
        return FACTORY.getValidator();
    }

    @Test
    void writesOneDecodablePngPerRowAndAReport() throws IOException {
        Map<String, byte[]> zip = run("""
                data,filename
                https://example.com/a,first
                https://example.com/b,second
                """, new BatchOptions("png", small(), null));

        assertThat(zip.keySet()).containsExactly("first.png", "second.png", "report.csv");
        assertThat(decode(zip.get("first.png"))).isEqualTo("https://example.com/a");
        assertThat(decode(zip.get("second.png"))).isEqualTo("https://example.com/b");
        assertThat(text(zip.get("report.csv"))).isEqualTo(
                "row,filename,status,message\r\n1,first,ok,\r\n2,second,ok,\r\n");
    }

    @Test
    void writesSvgAndBothFormats() throws IOException {
        Map<String, byte[]> svg = run("data\nhello\n", new BatchOptions("SVG", small(), null));
        assertThat(svg.keySet()).containsExactly("row-1.svg", "report.csv");
        assertThat(text(svg.get("row-1.svg"))).startsWith("<?xml").contains("<svg");

        Map<String, byte[]> both = run("data\nhello\n", new BatchOptions("both", small(), null));
        assertThat(both.keySet()).containsExactly("row-1.png", "row-1.svg", "report.csv");
        assertThat(decode(both.get("row-1.png"))).isEqualTo("hello");
    }

    @Test
    void defaultsToPngAndAPlainStyle() throws IOException {
        Map<String, byte[]> zip = run("data\nhello\n", null);

        assertThat(zip.keySet()).containsExactly("row-1.png", "report.csv");
        assertThat(ImageIO.read(new ByteArrayInputStream(zip.get("row-1.png"))).getWidth()).isEqualTo(512);
    }

    @Test
    void skipsRowsThatCannotRenderAndReportsThem() throws IOException {
        String tooLong = "x".repeat(3000);
        Map<String, byte[]> zip = run("data,filename\n"
                + "ok,good\n"
                + ",empty\n"
                + "\"   \",spaces\n"
                + tooLong + ",long\n"
                + "y".repeat(8001) + ",huge\n",
                new BatchOptions("png", new QrStyle(128, null, ErrorCorrection.HIGH, null, null,
                        null, null, null, null, null, null, null, null), null));

        assertThat(zip.keySet()).containsExactly("good.png", "report.csv");
        List<String> report = text(zip.get("report.csv")).lines().toList();
        assertThat(report).containsExactly(
                "row,filename,status,message",
                "1,good,ok,",
                "2,empty,skipped,data must not be blank",
                "3,spaces,skipped,data must not be blank",
                "4,long,skipped,The content is too long for a QR code at HIGH error correction."
                        + " Shorten it or choose a lower level.",
                "5,huge,skipped,data must be at most 8000 characters");
    }

    @Test
    void sanitisesAndDeduplicatesNames() throws IOException {
        Map<String, byte[]> zip = run("""
                filename,data
                ../../evil,a
                ../../evil,b
                ,c
                Flyer.png,d
                flyer,e
                """, new BatchOptions(null, small(), null));

        assertThat(zip.keySet()).containsExactly(
                "evil.png", "evil-2.png", "row-3.png", "Flyer.png", "flyer-2.png", "report.csv");
        assertThat(zip.keySet()).noneMatch(name -> name.contains("/") || name.contains(".."));
    }

    @Test
    void keepsMultilinePayloadsIntact() throws IOException {
        String vcard = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ann Example\r\nEND:VCARD";
        Map<String, byte[]> zip = run("data,filename\r\n\"" + vcard + "\",ann\r\n",
                new BatchOptions("png", small(), null));

        assertThat(decode(zip.get("ann.png"))).isEqualTo(vcard);
    }

    @Test
    void refusesMoreThanTheRowLimit() {
        StringBuilder csv = new StringBuilder("data\n");
        for (int i = 0; i <= BatchService.MAX_ROWS; i++) {
            csv.append("row ").append(i).append('\n');
        }

        assertThatThrownBy(() -> prepare(csv.toString(), null))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessage("The CSV has more than 500 rows. Split it into smaller batches.");
    }

    @Test
    void acceptsExactlyTheRowLimit() {
        StringBuilder csv = new StringBuilder("data\n");
        for (int i = 0; i < BatchService.MAX_ROWS; i++) {
            csv.append("row ").append(i).append('\n');
        }

        assertThat(prepare(csv.toString(), null).rows()).hasSize(BatchService.MAX_ROWS);
    }

    @Test
    void refusesAnOversizedCsv() {
        byte[] csv = new byte[BatchService.MAX_CSV_BYTES + 1];

        assertThatThrownBy(() -> service.prepare(csv, null, CALLER))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessage("The CSV must be at most 1 MB.");
    }

    @Test
    void refusesACsvWithoutADataColumnHeaderOrRows() {
        assertThatThrownBy(() -> prepare("url,filename\nhttps://example.com,a\n", null))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessage("The CSV's header row has no data column. Found: url, filename.");
        assertThatThrownBy(() -> prepare("", null))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageStartingWith("The CSV is empty.");
        assertThatThrownBy(() -> prepare("data\n\n\n", null))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessage("The CSV has a header but no rows.");
    }

    @Test
    void readsTheHeaderWithoutRegardToCaseOrPadding() {
        assertThat(BatchService.read("\" Data \",FileName\nhello,greeting\n"))
                .containsExactly(new BatchService.Row(1, "hello", "greeting"));
    }

    @Test
    void refusesAStyleAndADesignTogether() {
        assertThatThrownBy(() -> prepare("data\na\n", new BatchOptions("png", small(), 7L)))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessage("Send a style or a designId, not both.");
    }

    @Test
    void checksTheStyleBeforeAnythingIsRendered() {
        QrStyle unreadable = new QrStyle(256, null, null, null,
                new QrGradient(GradientType.LINEAR, "#fefefe", "#ffffff", 0, false),
                "#ffffff", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> prepare("data\na\n", new BatchOptions("png", unreadable, null)))
                .isInstanceOf(GradientException.class);
    }

    private BatchService.Batch prepare(String csv, BatchOptions options) {
        return service.prepare(csv.getBytes(StandardCharsets.UTF_8), options, CALLER);
    }

    private Map<String, byte[]> run(String csv, BatchOptions options) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        prepare(csv, options).writeTo(out);
        return entries(out.toByteArray());
    }

    static Map<String, byte[]> entries(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static QrStyle small() {
        return new QrStyle(200, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String decode(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        try {
            return new MultiFormatReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))))
                    .getText();
        } catch (NotFoundException e) {
            throw new AssertionError("The PNG did not decode as a QR code.", e);
        }
    }
}

package org.skrall.qrcode.batch;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.skrall.qrcode.BackgroundImageException;
import org.skrall.qrcode.FinderArtworkException;
import org.skrall.qrcode.FrameException;
import org.skrall.qrcode.GradientException;
import org.skrall.qrcode.LogoException;
import org.skrall.qrcode.QrEncodingException;
import org.skrall.qrcode.design.DesignService;
import org.skrall.qrcode.model.BatchOptions;
import org.skrall.qrcode.model.QrCodeRequest;
import org.skrall.qrcode.model.QrFormat;
import org.skrall.qrcode.model.QrStyle;
import org.skrall.qrcode.security.Caller;
import org.skrall.qrcode.service.QrCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Renders one style over every row of a CSV into a ZIP.
 *
 * <p>Work is split in two. {@link #prepare} does everything that can reject the batch as a
 * whole — reading the CSV, the limits, resolving a saved design, decoding and checking the style
 * — while the request can still answer 400 or 404. {@link Batch#writeTo} then renders row by row
 * straight into the response, so only one row's images are held at a time. A row that cannot be
 * rendered is skipped and listed in {@code report.csv}, the archive's last entry, rather than
 * failing a download that has already begun.
 */
@Service
public class BatchService {

    /** The most data rows one batch takes. */
    public static final int MAX_ROWS = 500;

    /** The largest CSV accepted, in bytes. */
    public static final int MAX_CSV_BYTES = 1024 * 1024;

    public static final String REPORT = "report.csv";

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private final QrCodeService qrCodes;
    private final DesignService designs;
    private final Validator validator;

    public BatchService(QrCodeService qrCodes, DesignService designs, Validator validator) {
        this.qrCodes = qrCodes;
        this.designs = designs;
        this.validator = validator;
    }

    /**
     * One planned row.
     *
     * @param row  its number, 1 for the first row after the header, blank lines not counted
     * @param data the payload exactly as written
     * @param name the entry name without extension
     */
    public record Row(int row, String data, String name) {
    }

    /**
     * Checks the whole batch and plans its entries, without rendering anything.
     *
     * @throws InvalidBatchException when the CSV or the options are unusable
     * @throws org.skrall.qrcode.design.DesignNotFoundException when the design is not visible
     *         to the caller
     * @throws RuntimeException the style's own rejections, as the render API raises them
     */
    public Batch prepare(byte[] csv, BatchOptions options, Caller caller) {
        BatchOptions effective = options == null ? BatchOptions.DEFAULTS : options;
        if (csv.length > MAX_CSV_BYTES) {
            throw new InvalidBatchException("The CSV must be at most 1 MB.");
        }
        if (effective.style() != null && effective.designId() != null) {
            throw new InvalidBatchException("Send a style or a designId, not both.");
        }
        List<Row> rows = read(CsvReader.decode(csv));
        QrStyle style = effective.designId() == null
                ? effective.style()
                : designs.get(effective.designId(), caller).style();
        return new Batch(rows, effective.formats(), qrCodes.styled(style));
    }

    static List<Row> read(String text) {
        CsvReader reader = new CsvReader(text);
        CsvReader.Record header = reader.next();
        if (header == null) {
            throw new InvalidBatchException("The CSV is empty. Its first row must name the columns,"
                    + " with a data column and optionally a filename column.");
        }
        int dataColumn = column(header, "data");
        int nameColumn = column(header, "filename");
        if (dataColumn < 0) {
            throw new InvalidBatchException("The CSV's header row has no data column. Found: "
                    + header.fields().stream().map(String::strip).collect(Collectors.joining(", "))
                    + ".");
        }

        FileNames names = new FileNames();
        List<Row> rows = new ArrayList<>();
        for (CsvReader.Record record = reader.next(); record != null; record = reader.next()) {
            if (rows.size() == MAX_ROWS) {
                throw new InvalidBatchException(
                        "The CSV has more than " + MAX_ROWS + " rows. Split it into smaller batches.");
            }
            int row = rows.size() + 1;
            String requested = nameColumn < 0 ? null : record.field(nameColumn);
            rows.add(new Row(row, record.field(dataColumn), names.assign(requested, row)));
        }
        if (rows.isEmpty()) {
            throw new InvalidBatchException("The CSV has a header but no rows.");
        }
        return rows;
    }

    private static int column(CsvReader.Record header, String name) {
        for (int i = 0; i < header.fields().size(); i++) {
            if (header.fields().get(i).strip().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** A checked batch, ready to stream. */
    public final class Batch {

        private final List<Row> rows;
        private final List<QrFormat> formats;
        private final QrCodeService.Styled styled;

        private Batch(List<Row> rows, List<QrFormat> formats, QrCodeService.Styled styled) {
            this.rows = rows;
            this.formats = formats;
            this.styled = styled;
        }

        public List<Row> rows() {
            return rows;
        }

        /**
         * Writes the archive: each rendered row's images, then {@code report.csv}. The stream is
         * finished but not closed.
         */
        public void writeTo(OutputStream out) throws IOException {
            ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
            StringBuilder report = new StringBuilder("row,filename,status,message\r\n");
            for (Row row : rows) {
                String problem = render(row, zip);
                report.append(row.row()).append(',')
                        .append(CsvReader.quote(row.name())).append(',')
                        .append(problem == null ? "ok" : "skipped").append(',')
                        .append(problem == null ? "" : CsvReader.quote(problem)).append("\r\n");
            }
            zip.putNextEntry(new ZipEntry(REPORT));
            Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
            writer.write(report.toString());
            writer.flush();
            zip.closeEntry();
            zip.finish();
            zip.flush();
        }

        /** Renders one row into the archive, or says why it was skipped. */
        private String render(Row row, ZipOutputStream zip) throws IOException {
            String invalid = validator.validate(new QrCodeRequest(row.data(), null)).stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining(" "));
            if (!invalid.isEmpty()) {
                return invalid;
            }
            Map<QrFormat, byte[]> images;
            try {
                images = styled.render(row.data(), formats);
            } catch (QrEncodingException | FrameException | LogoException | FinderArtworkException
                    | BackgroundImageException | GradientException e) {
                return e.getMessage();
            } catch (RuntimeException e) {
                log.warn("Batch row {} could not be rendered", row.row(), e);
                return "This row could not be rendered.";
            }
            for (QrFormat format : formats) {
                write(zip, row.name() + "." + format.extension(), images.get(format), format);
            }
            return null;
        }
    }

    /**
     * Writes one entry. A PNG is already compressed, so it is stored rather than deflated again;
     * an SVG is text and deflates well.
     */
    private static void write(ZipOutputStream zip, String name, byte[] bytes, QrFormat format)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        if (format == QrFormat.PNG) {
            CRC32 crc = new CRC32();
            crc.update(bytes);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            entry.setCrc(crc.getValue());
        }
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}

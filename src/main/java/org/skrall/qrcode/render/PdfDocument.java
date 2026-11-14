package org.skrall.qrcode.render;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;

/**
 * A minimal PDF file writer: numbered objects, Flate streams, the cross-reference table.
 *
 * <p>Only what a one-page QR code needs. Dictionaries are passed in as PDF source text, which keeps
 * the renderer readable next to the PDF specification it follows (ISO 32000-1).
 */
final class PdfDocument {

    private final List<byte[]> objects = new ArrayList<>();

    /** Reserves an object number to be filled in later with {@link #set}. */
    int reserve() {
        objects.add(null);
        return objects.size();
    }

    /** Adds a non-stream object, such as a dictionary or an array, and returns its number. */
    int add(String object) {
        int number = reserve();
        set(number, object);
        return number;
    }

    void set(int number, String object) {
        objects.set(number - 1, object.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * Adds a stream compressed with Flate.
     *
     * @param dictionary the stream's dictionary entries, without {@code /Length} or
     *                   {@code /Filter}, and without the enclosing {@code << >>}
     */
    int addFlateStream(String dictionary, byte[] data) {
        byte[] compressed = deflate(data);
        byte[] head = ("<< " + dictionary + " /Filter /FlateDecode /Length " + compressed.length
                + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1);
        byte[] tail = "\nendstream".getBytes(StandardCharsets.ISO_8859_1);
        byte[] object = new byte[head.length + compressed.length + tail.length];
        System.arraycopy(head, 0, object, 0, head.length);
        System.arraycopy(compressed, 0, object, head.length, compressed.length);
        System.arraycopy(tail, 0, object, head.length + compressed.length, tail.length);
        int number = reserve();
        objects.set(number - 1, object);
        return number;
    }

    /** The file, with {@code catalog} as the root and {@code info} as the document information. */
    byte[] toBytes(int catalog, int info) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // A comment of high bytes after the header tells transfer tools the file is binary.
        write(out, "%PDF-1.4\n%âãÏÓ\n");
        long[] offsets = new long[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            byte[] object = objects.get(i);
            if (object == null) {
                throw new IllegalStateException("PDF object " + (i + 1) + " was reserved but never set");
            }
            offsets[i] = out.size();
            write(out, (i + 1) + " 0 obj\n");
            out.writeBytes(object);
            write(out, "\nendobj\n");
        }
        long xref = out.size();
        StringBuilder table = new StringBuilder();
        table.append("xref\n0 ").append(objects.size() + 1).append('\n');
        table.append("0000000000 65535 f \n");
        for (long offset : offsets) {
            table.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        table.append("trailer\n<< /Size ").append(objects.size() + 1)
                .append(" /Root ").append(catalog).append(" 0 R")
                .append(" /Info ").append(info).append(" 0 R >>\n")
                .append("startxref\n").append(xref).append("\n%%EOF\n");
        write(out, table.toString());
        return out.toByteArray();
    }

    /** A number in PDF syntax: at most four decimals, no exponent, no negative zero. */
    static String num(double value) {
        String text = BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        return "-0".equals(text) ? "0" : text;
    }

    /**
     * A text string for the document information: plain where it is printable ASCII, otherwise
     * UTF-16BE with a byte order mark, written as hex so no escaping is needed.
     */
    static String text(String value) {
        boolean ascii = value.chars().allMatch(c -> c >= 0x20 && c < 0x7f);
        if (ascii) {
            return "(" + value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")";
        }
        StringBuilder hex = new StringBuilder("<FEFF");
        for (byte b : value.getBytes(StandardCharsets.UTF_16BE)) {
            hex.append(String.format("%02X", b));
        }
        return hex.append('>').toString();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 4));
            byte[] buffer = new byte[16384];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }
}

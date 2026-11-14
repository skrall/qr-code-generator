package org.skrall.qrcode.batch;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads RFC 4180 CSV one record at a time.
 *
 * <p>Fields are separated by commas and records by CRLF, LF or a lone CR. A field in double
 * quotes may hold commas, line breaks and doubled quotes ({@code ""} for one {@code "}). A quote
 * inside an unquoted field is kept as text, which is what spreadsheets export for
 * {@code say "hi"}; text after a closing quote, and a quote that is never closed, are errors that
 * name the line. Empty lines between records are skipped. Nothing is trimmed: a payload is
 * encoded exactly as written.
 *
 * <p>Written here rather than taken from a library: the grammar is a few dozen lines, the batch
 * endpoint needs nothing beyond it, and it adds no dependency to audit or to the native image.
 */
public final class CsvReader {

    /**
     * One record.
     *
     * @param line   the line it starts on, counting from 1, for error messages
     * @param fields its fields, at least one
     */
    public record Record(int line, List<String> fields) {

        /** The field at {@code index}, or an empty string for a short record. */
        public String field(int index) {
            return index < fields.size() ? fields.get(index) : "";
        }
    }

    private final String text;
    private int position;
    private int line = 1;

    public CsvReader(String text) {
        this.text = text;
    }

    /**
     * Decodes UTF-8, dropping a leading byte order mark as spreadsheets on Windows write one.
     *
     * @throws InvalidBatchException when the bytes are not valid UTF-8
     */
    public static String decode(byte[] bytes) {
        int offset = bytes.length >= 3
                && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidBatchException("The CSV is not valid UTF-8. Save it as \"CSV UTF-8\".");
        }
    }

    /**
     * The next record, or {@code null} at the end of the text.
     *
     * @throws InvalidBatchException when a quoted field is malformed
     */
    public Record next() {
        while (position < text.length() && isLineBreak(text.charAt(position))) {
            consumeLineBreak();
        }
        if (position >= text.length()) {
            return null;
        }

        int start = line;
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        while (true) {
            if (position < text.length() && text.charAt(position) == '"') {
                readQuoted(field);
            } else {
                while (position < text.length() && !isSeparator(text.charAt(position))) {
                    field.append(text.charAt(position++));
                }
            }
            fields.add(field.toString());
            field.setLength(0);

            if (position >= text.length()) {
                break;
            }
            if (text.charAt(position) == ',') {
                position++;
                continue;
            }
            consumeLineBreak();
            break;
        }
        return new Record(start, List.copyOf(fields));
    }

    private void readQuoted(StringBuilder field) {
        int opened = line;
        position++;
        while (true) {
            if (position >= text.length()) {
                throw new InvalidBatchException(
                        "Line " + opened + " of the CSV opens a quoted field that is never closed.");
            }
            char c = text.charAt(position);
            if (c == '"') {
                if (position + 1 < text.length() && text.charAt(position + 1) == '"') {
                    field.append('"');
                    position += 2;
                    continue;
                }
                position++;
                break;
            }
            if (c == '\n' || (c == '\r' && !(position + 1 < text.length() && text.charAt(position + 1) == '\n'))) {
                line++;
            }
            field.append(c);
            position++;
        }
        if (position < text.length() && !isSeparator(text.charAt(position))) {
            throw new InvalidBatchException("Line " + line + " of the CSV has text after a closing"
                    + " quote. Inside a quoted field, write a quote as two quotes (\"\").");
        }
    }

    private void consumeLineBreak() {
        if (text.charAt(position) == '\r' && position + 1 < text.length() && text.charAt(position + 1) == '\n') {
            position++;
        }
        position++;
        line++;
    }

    private static boolean isSeparator(char c) {
        return c == ',' || isLineBreak(c);
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r';
    }

    /** One CSV field, quoted when it has to be. */
    public static String quote(String value) {
        if (value.isEmpty() || value.chars().noneMatch(c -> c == ',' || c == '"' || c == '\r' || c == '\n')) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}

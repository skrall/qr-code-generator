package org.skrall.qrcode.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvReaderTest {

    @Test
    void readsPlainFieldsAndRecords() {
        assertThat(all("data,filename\nhttps://a.example,a\nhttps://b.example,b\n")).containsExactly(
                List.of("data", "filename"), List.of("https://a.example", "a"), List.of("https://b.example", "b"));
    }

    @Test
    void acceptsCrlfAndALoneCrAndNoFinalLineBreak() {
        assertThat(all("a,b\r\n1,2\r3,4")).containsExactly(List.of("a", "b"), List.of("1", "2"), List.of("3", "4"));
    }

    @Test
    void quotedFieldsHoldCommasQuotesAndLineBreaks() {
        String text = "data\r\n\"x,y\"\r\n\"say \"\"hi\"\"\"\r\n\"BEGIN:VCARD\r\nFN:Ann\r\nEND:VCARD\"\r\n";

        assertThat(all(text)).containsExactly(
                List.of("data"), List.of("x,y"), List.of("say \"hi\""), List.of("BEGIN:VCARD\r\nFN:Ann\r\nEND:VCARD"));
    }

    @Test
    void keepsEmptyFieldsAndWhitespace() {
        assertThat(all("a,b,c\n, x ,\n\"\",\"\",\"\"\n")).containsExactly(
                List.of("a", "b", "c"), List.of("", " x ", ""), List.of("", "", ""));
    }

    @Test
    void keepsAQuoteInsideAnUnquotedField() {
        assertThat(all("data\nsay \"hi\"\n")).containsExactly(List.of("data"), List.of("say \"hi\""));
    }

    @Test
    void skipsBlankLinesAndCountsLinesAcrossQuotedBreaks() {
        CsvReader reader = new CsvReader("data\n\n\"one\ntwo\"\n\r\n\nlast\n\n");

        assertThat(reader.next().line()).isEqualTo(1);
        CsvReader.Record multiline = reader.next();
        assertThat(multiline.line()).isEqualTo(3);
        assertThat(multiline.fields()).containsExactly("one\ntwo");
        CsvReader.Record last = reader.next();
        assertThat(last.line()).isEqualTo(7);
        assertThat(last.fields()).containsExactly("last");
        assertThat(reader.next()).isNull();
    }

    @Test
    void aShortRecordReadsMissingFieldsAsEmpty() {
        CsvReader.Record record = new CsvReader("only").next();

        assertThat(record.field(0)).isEqualTo("only");
        assertThat(record.field(3)).isEmpty();
    }

    @Test
    void anUnclosedQuoteNamesTheLineItOpenedOn() {
        CsvReader reader = new CsvReader("data\n\"never\nclosed\n");
        reader.next();

        assertThatThrownBy(reader::next)
                .isInstanceOf(InvalidBatchException.class)
                .hasMessage("Line 2 of the CSV opens a quoted field that is never closed.");
    }

    @Test
    void textAfterAClosingQuoteIsAnError() {
        CsvReader reader = new CsvReader("data\n\"a\"b\n");
        reader.next();

        assertThatThrownBy(reader::next)
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageStartingWith("Line 2 of the CSV has text after a closing quote.");
    }

    @Test
    void decodesUtf8AndDropsTheByteOrderMark() {
        byte[] withBom = "﻿data\nCafé ☕\n".getBytes(StandardCharsets.UTF_8);
        assertThat(withBom[0]).isEqualTo((byte) 0xEF);

        String text = CsvReader.decode(withBom);

        assertThat(text).isEqualTo("data\nCafé ☕\n");
        assertThat(CsvReader.decode("data\n".getBytes(StandardCharsets.UTF_8))).isEqualTo("data\n");
    }

    @Test
    void refusesBytesThatAreNotUtf8() {
        byte[] latin1 = "data\nCafé\n".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> CsvReader.decode(latin1))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("not valid UTF-8");
    }

    @Test
    void quotesOnlyWhatNeedsIt() {
        assertThat(CsvReader.quote("plain")).isEqualTo("plain");
        assertThat(CsvReader.quote("")).isEmpty();
        assertThat(CsvReader.quote("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvReader.quote("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvReader.quote("two\nlines")).isEqualTo("\"two\nlines\"");
    }

    @Test
    void whatItQuotesReadsBack() {
        String value = "a, \"b\"\r\nc";

        assertThat(all("x\n" + CsvReader.quote(value) + "\n").get(1)).containsExactly(value);
    }

    private static List<List<String>> all(String text) {
        CsvReader reader = new CsvReader(text);
        List<List<String>> records = new ArrayList<>();
        for (CsvReader.Record record = reader.next(); record != null; record = reader.next()) {
            records.add(record.fields());
        }
        return records;
    }
}

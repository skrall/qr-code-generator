package org.skrall.qrcode.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileNamesTest {

    @Test
    void keepsASafeName() {
        assertThat(FileNames.sanitise("spring-flyer_01")).isEqualTo("spring-flyer_01");
        assertThat(FileNames.sanitise("Café")).isEqualTo("Café");
    }

    @Test
    void replacesPathSeparatorsAndUnsafeCharacters() {
        assertThat(FileNames.sanitise("../../etc/passwd")).isEqualTo("etc_passwd");
        assertThat(FileNames.sanitise("C:\\Windows\\win.ini")).isEqualTo("C_Windows_win.ini");
        assertThat(FileNames.sanitise("a b\tc*?<>|\"d")).isEqualTo("a_b_c_d");
        assertThat(FileNames.sanitise("nul\u0000byte")).isEqualTo("nul_byte");
    }

    @Test
    void neverLeavesDotDotOrAHiddenName() {
        assertThat(FileNames.sanitise("a..b")).isEqualTo("a.b");
        assertThat(FileNames.sanitise(".hidden")).isEqualTo("hidden");
        assertThat(FileNames.sanitise("..")).isNull();
        assertThat(FileNames.sanitise("trailing.")).isEqualTo("trailing");
    }

    @Test
    void dropsTheImageExtensionItWillAddItself() {
        assertThat(FileNames.sanitise("logo.PNG")).isEqualTo("logo");
        assertThat(FileNames.sanitise("logo.svg")).isEqualTo("logo");
        assertThat(FileNames.sanitise("report.pdf")).isEqualTo("report.pdf");
    }

    @Test
    void limitsTheLength() {
        String name = FileNames.sanitise("x".repeat(300));

        assertThat(name).hasSize(FileNames.MAX_LENGTH);
    }

    @Test
    void suffixesWindowsDeviceNames() {
        assertThat(FileNames.sanitise("CON")).isEqualTo("CON_");
        assertThat(FileNames.sanitise("lpt1")).isEqualTo("lpt1_");
        assertThat(FileNames.sanitise("console")).isEqualTo("console");
    }

    @Test
    void blankOrUnusableNamesFallBackToTheRowNumber() {
        FileNames names = new FileNames();

        assertThat(names.assign(null, 1)).isEqualTo("row-1");
        assertThat(names.assign("   ", 2)).isEqualTo("row-2");
        assertThat(names.assign("///", 3)).isEqualTo("row-3");
    }

    @Test
    void deduplicatesWithoutRegardToCase() {
        FileNames names = new FileNames();

        assertThat(names.assign("flyer", 1)).isEqualTo("flyer");
        assertThat(names.assign("Flyer", 2)).isEqualTo("Flyer-2");
        assertThat(names.assign("flyer.png", 3)).isEqualTo("flyer-3");
        // An explicit name that a suffix already took is suffixed in turn.
        assertThat(names.assign("flyer-2", 4)).isEqualTo("flyer-2-2");
        // And a default name is deduplicated like any other.
        assertThat(names.assign("row-6", 5)).isEqualTo("row-6");
        assertThat(names.assign("", 6)).isEqualTo("row-6-2");
    }
}

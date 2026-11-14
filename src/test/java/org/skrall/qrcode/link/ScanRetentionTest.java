package org.skrall.qrcode.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** A retention of 0 keeps every scan. */
@SpringBootTest(properties = "app.links.scan-retention-days=0")
class ScanRetentionTest {

    @Autowired
    private ScanService service;

    @Autowired
    private LinkScanRepository scans;

    @Autowired
    private ShortLinkService links;

    @Test
    void zeroKeepsScansForever() {
        ShortLink link = links.create("https://example.com/forever", null);
        scans.save(new LinkScan(link.getId(), Instant.parse("2001-01-01T00:00:00Z"), ScanSource.UNKNOWN));

        assertThat(service.purge(Instant.now())).isZero();
        assertThat(scans.count()).isPositive();
    }

    @Test
    void aNegativeRetentionIsRefused() {
        assertThatThrownBy(() -> new ScanProperties(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scan-retention-days");
    }
}

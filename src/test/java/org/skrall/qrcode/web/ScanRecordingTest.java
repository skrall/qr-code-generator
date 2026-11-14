package org.skrall.qrcode.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.link.DeviceClass;
import org.skrall.qrcode.link.LinkScan;
import org.skrall.qrcode.link.LinkScanRepository;
import org.skrall.qrcode.link.ShortLink;
import org.skrall.qrcode.link.ShortLinkRepository;
import org.skrall.qrcode.link.ShortLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** What a redirect records, through the public endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
class ScanRecordingTest {

    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1";
    private static final String SLACKBOT = "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService service;

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private LinkScanRepository scans;

    @BeforeEach
    void clear() {
        scans.deleteAll();
        links.deleteAll();
    }

    @Test
    void recordsTheDeviceAndOnlyTheReferrersHost() throws Exception {
        ShortLink link = service.create("https://example.com", null);
        Instant before = Instant.now();

        mockMvc.perform(get("/r/{code}", link.getCode())
                        .header("User-Agent", IPHONE)
                        .header("Referer", "https://mail.example.org/inbox?token=secret"))
                .andExpect(status().isFound());

        assertThat(scans.findAll()).singleElement().satisfies(scan -> {
            assertThat(scan.getLinkId()).isEqualTo(link.getId());
            assertThat(scan.getDevice()).isEqualTo(DeviceClass.IOS);
            assertThat(scan.isBot()).isFalse();
            assertThat(scan.getReferrerHost()).isEqualTo("mail.example.org");
            assertThat(scan.getScannedAt()).isBetween(before, Instant.now());
        });
    }

    @Test
    void aBotIsRedirectedAndRecordedButNotCountedAsAHit() throws Exception {
        ShortLink link = service.create("https://example.com", null);

        mockMvc.perform(get("/r/{code}", link.getCode()).header("User-Agent", SLACKBOT))
                .andExpect(status().isFound());
        mockMvc.perform(get("/r/{code}", link.getCode()).header("User-Agent", IPHONE))
                .andExpect(status().isFound());

        assertThat(scans.findAll()).extracting(LinkScan::isBot).containsExactlyInAnyOrder(true, false);
        ShortLink reloaded = links.findByCode(link.getCode()).orElseThrow();
        assertThat(reloaded.getHitCount()).isEqualTo(1);
    }

    @Test
    void onlyAResolvedRedirectIsRecorded() throws Exception {
        ShortLink removed = service.create("https://example.com/removed", null);
        service.delete(removed.getCode());

        mockMvc.perform(get("/r/{code}", removed.getCode()).header("User-Agent", IPHONE))
                .andExpect(status().isGone());
        mockMvc.perform(get("/r/{code}", "nosuchcode").header("User-Agent", IPHONE))
                .andExpect(status().isNotFound());

        assertThat(scans.count()).isZero();
    }

    @Test
    void aGoneLinksFallbackRedirectIsNotRecorded() throws Exception {
        ShortLink removed = service.create("https://example.com/old", null, "https://example.com/next", null);
        service.delete(removed.getCode());

        mockMvc.perform(get("/r/{code}", removed.getCode()).header("User-Agent", IPHONE))
                .andExpect(status().isFound());

        assertThat(scans.count()).isZero();
    }
}

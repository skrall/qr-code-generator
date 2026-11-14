package org.skrall.qrcode.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.model.LinkStatsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The stats and retention queries against H2, through the real migrations. */
@SpringBootTest(properties = "app.links.scan-retention-days=30")
class ScanServiceTest {

    private static final ScanSource IPHONE = new ScanSource(DeviceClass.IOS, false, null);
    private static final ScanSource ANDROID = new ScanSource(DeviceClass.ANDROID, false, null);
    private static final ScanSource SLACKBOT = new ScanSource(DeviceClass.OTHER, true, null);

    @Autowired
    private ScanService service;

    @Autowired
    private LinkScanRepository scans;

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private ShortLinkService linkService;

    private ShortLink link;

    @BeforeEach
    void setUp() {
        scans.deleteAll();
        links.deleteAll();
        link = linkService.create("https://example.com/stats", null);
    }

    private void scan(String at, ScanSource source) {
        scans.save(new LinkScan(link.getId(), Instant.parse(at), source));
    }

    @Test
    void fillsEveryDayOfThePeriodWithZeroesWhereNothingHappened() {
        scan("2026-03-08T12:00:00Z", IPHONE);
        scan("2026-03-10T09:00:00Z", IPHONE);
        scan("2026-03-10T10:00:00Z", SLACKBOT);

        LinkStatsResponse stats = service.stats(link.getCode(), 7, Instant.parse("2026-03-10T18:00:00Z"))
                .orElseThrow();

        assertThat(stats.from()).isEqualTo(LocalDate.parse("2026-03-04"));
        assertThat(stats.to()).isEqualTo(LocalDate.parse("2026-03-10"));
        assertThat(stats.timeZone()).isEqualTo("UTC");
        assertThat(stats.daily()).hasSize(7);
        assertThat(stats.daily()).extracting(LinkStatsResponse.Day::scans).containsExactly(0L, 0L, 0L, 0L, 1L, 0L, 1L);
        assertThat(stats.daily()).extracting(LinkStatsResponse.Day::bots).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 1L);
        assertThat(stats.daily().getFirst().date()).isEqualTo(LocalDate.parse("2026-03-04"));
        assertThat(stats.scans()).isEqualTo(2);
        assertThat(stats.bots()).isEqualTo(1);
    }

    @Test
    void daysAreUtcDaysAndThePeriodEndsToday() {
        scan("2026-03-08T23:59:59Z", IPHONE);   // before the period
        scan("2026-03-09T00:00:00Z", IPHONE);   // first instant of the first day
        scan("2026-03-09T23:59:59Z", IPHONE);   // last instant of the first day
        scan("2026-03-10T00:00:00Z", IPHONE);   // today

        LinkStatsResponse stats = service.stats(link.getCode(), 2, Instant.parse("2026-03-10T00:30:00Z"))
                .orElseThrow();

        assertThat(stats.daily()).extracting(LinkStatsResponse.Day::date)
                .containsExactly(LocalDate.parse("2026-03-09"), LocalDate.parse("2026-03-10"));
        assertThat(stats.daily()).extracting(LinkStatsResponse.Day::scans).containsExactly(2L, 1L);
        assertThat(stats.scans()).isEqualTo(3);
    }

    @Test
    void breaksPeopleDownByDeviceAndReferrerLeavingBotsOut() {
        scan("2026-03-10T09:00:00Z", IPHONE);
        scan("2026-03-10T09:01:00Z", IPHONE);
        scan("2026-03-10T09:02:00Z", new ScanSource(DeviceClass.IOS, false, "l.instagram.com"));
        scan("2026-03-10T09:03:00Z", new ScanSource(DeviceClass.ANDROID, false, "l.instagram.com"));
        scan("2026-03-10T09:04:00Z", new ScanSource(DeviceClass.WINDOWS, false, "mail.example.com"));
        scan("2026-03-10T09:05:00Z", new ScanSource(DeviceClass.OTHER, true, "slack.com"));

        LinkStatsResponse stats = service.stats(link.getCode(), 30, Instant.parse("2026-03-10T18:00:00Z"))
                .orElseThrow();

        assertThat(stats.devices()).containsExactly(
                new LinkStatsResponse.Device(DeviceClass.IOS, 3),
                new LinkStatsResponse.Device(DeviceClass.ANDROID, 1),
                new LinkStatsResponse.Device(DeviceClass.WINDOWS, 1));
        assertThat(stats.referrers()).containsExactly(
                new LinkStatsResponse.Referrer("l.instagram.com", 2),
                new LinkStatsResponse.Referrer(null, 2),
                new LinkStatsResponse.Referrer("mail.example.com", 1));
    }

    @Test
    void listsAtMostTheTopTenReferrers() {
        for (int i = 0; i < 12; i++) {
            for (int n = 0; n <= i; n++) {
                scan("2026-03-10T09:00:00Z", new ScanSource(DeviceClass.IOS, false, "host" + i + ".example"));
            }
        }

        LinkStatsResponse stats = service.stats(link.getCode(), 1, Instant.parse("2026-03-10T18:00:00Z"))
                .orElseThrow();

        assertThat(stats.referrers()).hasSize(ScanService.TOP_REFERRERS);
        assertThat(stats.referrers().getFirst()).isEqualTo(new LinkStatsResponse.Referrer("host11.example", 12));
    }

    @Test
    void onlyCountsTheLinkAskedFor() {
        ShortLink other = linkService.create("https://example.com/other", null);
        scans.save(new LinkScan(other.getId(), Instant.parse("2026-03-10T09:00:00Z"), ANDROID));
        scan("2026-03-10T09:00:00Z", IPHONE);

        LinkStatsResponse stats = service.stats(link.getCode(), 1, Instant.parse("2026-03-10T18:00:00Z"))
                .orElseThrow();

        assertThat(stats.scans()).isEqualTo(1);
        assertThat(stats.devices()).containsExactly(new LinkStatsResponse.Device(DeviceClass.IOS, 1));
    }

    @Test
    void aDeletedLinkKeepsItsHistory() {
        scan("2026-03-10T09:00:00Z", IPHONE);
        linkService.delete(link.getCode());

        LinkStatsResponse stats = service.stats(link.getCode(), 7, Instant.parse("2026-03-10T18:00:00Z"))
                .orElseThrow();

        assertThat(stats.deleted()).isTrue();
        assertThat(stats.scans()).isEqualTo(1);
    }

    @Test
    void anUnknownCodeHasNoStats() {
        assertThat(service.stats("nosuchcode", 7, Instant.now())).isEmpty();
    }

    @Test
    void rejectsAPeriodOutsideOneToAYear() {
        assertThatThrownBy(() -> service.stats(link.getCode(), 0, Instant.now()))
                .isInstanceOf(InvalidLinkException.class);
        assertThatThrownBy(() -> service.stats(link.getCode(), 366, Instant.now()))
                .isInstanceOf(InvalidLinkException.class);
    }

    @Test
    void reportsTheHitCountAsAllTimeAndTheRetention() {
        linkService.resolve(link.getCode(), IPHONE);
        linkService.resolve(link.getCode(), IPHONE);

        LinkStatsResponse stats = service.stats(link.getCode(), 7, Instant.now()).orElseThrow();

        assertThat(stats.allTime()).isEqualTo(2);
        assertThat(stats.retentionDays()).isEqualTo(30);
    }

    @Test
    void purgeKeepsExactlyTheRetentionWindow() {
        // Retention 30 on 2026-03-31 keeps 2026-03-02 through 2026-03-31.
        scan("2026-03-01T23:59:59Z", IPHONE);
        scan("2026-03-02T00:00:00Z", IPHONE);
        scan("2026-03-31T12:00:00Z", SLACKBOT);

        int removed = service.purge(Instant.parse("2026-03-31T03:17:00Z"));

        assertThat(removed).isEqualTo(1);
        assertThat(scans.findAll()).extracting(LinkScan::getScannedOn)
                .containsExactlyInAnyOrder(LocalDate.parse("2026-03-02"), LocalDate.parse("2026-03-31"));
    }

    /** The scheduler calls this entry point, whose call to purge never passes the proxy. */
    @Test
    void theScheduledCleanupRunsInATransaction() {
        scans.save(new LinkScan(link.getId(), Instant.now().minus(Duration.ofDays(400)), IPHONE));
        scans.save(new LinkScan(link.getId(), Instant.now(), IPHONE));

        service.purgeOnSchedule();

        assertThat(scans.count()).isEqualTo(1);
    }
}

package org.skrall.qrcode.link;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.skrall.qrcode.model.LinkStatsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records scans, reports on them and prunes them.
 *
 * <p>Days are UTC days throughout. A viewer's own day boundaries would need their zone on every
 * request and would make a stored day ambiguous; UTC keeps the stored {@code scanned_on} the
 * single answer to "which day was this".
 */
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    static final int MAX_DAYS = 365;
    static final int TOP_REFERRERS = 10;

    private final LinkScanRepository scans;
    private final ShortLinkRepository links;
    private final ScanProperties properties;

    public ScanService(LinkScanRepository scans, ShortLinkRepository links, ScanProperties properties) {
        this.scans = scans;
        this.links = links;
        this.properties = properties;
    }

    /** Stores one scan. Called after the redirect's own transaction has committed. */
    @Transactional
    public void record(Long linkId, ScanSource source, Instant at) {
        scans.save(new LinkScan(linkId, at, source));
    }

    /**
     * The link's scans over the last {@code days} UTC days, today included, or empty for an
     * unknown code. A deleted link still answers: the row is kept for exactly this kind of
     * history, and its scans age out with the retention like any other.
     */
    @Transactional(readOnly = true)
    public Optional<LinkStatsResponse> stats(String code, int days, Instant now) {
        if (days < 1 || days > MAX_DAYS) {
            throw new InvalidLinkException("days must be between 1 and " + MAX_DAYS);
        }
        Optional<ShortLink> found = links.findByCode(code);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        ShortLink link = found.get();
        LocalDate to = LinkScan.utcDay(now);
        LocalDate from = to.minusDays(days - 1L);

        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (DailyScanCount count : scans.countByDay(link.getId(), from, to)) {
            byDay.computeIfAbsent(count.day(), day -> new long[2])[count.bot() ? 1 : 0] += count.scans();
        }
        List<LinkStatsResponse.Day> daily = new ArrayList<>(days);
        long people = 0;
        long bots = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            long[] counts = byDay.getOrDefault(day, new long[2]);
            daily.add(new LinkStatsResponse.Day(day, counts[0], counts[1]));
            people += counts[0];
            bots += counts[1];
        }

        List<LinkStatsResponse.Device> devices = scans.countByDevice(link.getId(), from, to).stream()
                .map(count -> new LinkStatsResponse.Device(DeviceClass.valueOf(count.key()), count.scans()))
                .toList();
        List<LinkStatsResponse.Referrer> referrers = scans
                .countByReferrer(link.getId(), from, to, Limit.of(TOP_REFERRERS)).stream()
                .map(count -> new LinkStatsResponse.Referrer(count.key(), count.scans()))
                .toList();

        return Optional.of(new LinkStatsResponse(
                link.getCode(), link.isDeleted(), days, from, to, "UTC",
                properties.scanRetentionDays(), link.getHitCount(), people, bots,
                daily, devices, referrers));
    }

    /**
     * The daily cleanup, at a quiet hour. Does nothing when retention is 0. Transactional itself
     * because the call to {@link #purge} is a self-call that the proxy never sees, and a bulk
     * delete outside a transaction is refused.
     */
    @Scheduled(cron = "${app.links.scan-cleanup-cron:0 17 3 * * *}", zone = "UTC")
    @Transactional
    public void purgeOnSchedule() {
        int removed = purge(Instant.now());
        if (removed > 0) {
            log.info("Removed {} scans older than {} days", removed, properties.scanRetentionDays());
        }
    }

    /**
     * Deletes the scans from before the retention window. A retention of {@code n} days keeps
     * today and the {@code n - 1} days before it, the same window a stats period of {@code n}
     * days covers.
     */
    @Transactional
    public int purge(Instant now) {
        int retention = properties.scanRetentionDays();
        if (retention == 0) {
            return 0;
        }
        return scans.deleteScannedBefore(LinkScan.utcDay(now).minusDays(retention - 1L));
    }
}

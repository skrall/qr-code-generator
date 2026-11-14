package org.skrall.qrcode.model;

import java.time.LocalDate;
import java.util.List;
import org.skrall.qrcode.link.DeviceClass;

/**
 * A link's scans over the last {@code days} UTC calendar days, today included.
 *
 * @param code          the link
 * @param deleted       whether the link has been deleted; its history stays readable
 * @param days          the length of the period
 * @param from          the first day of the period (UTC)
 * @param to            the last day of the period, today in UTC
 * @param timeZone      always {@code UTC}: the days of {@code daily} are UTC days
 * @param retentionDays how long scans are kept, 0 for forever; older days read as zero
 * @param allTime       the link's hit count: people since it was created, never pruned
 * @param scans         people in the period
 * @param bots          crawlers, link previews and scripts in the period
 * @param daily         one entry per day of the period, oldest first, zero-filled
 * @param devices       people in the period by device class, most first
 * @param referrers     people in the period by referrer host (null for none), top ten
 */
public record LinkStatsResponse(
        String code,
        boolean deleted,
        int days,
        LocalDate from,
        LocalDate to,
        String timeZone,
        int retentionDays,
        long allTime,
        long scans,
        long bots,
        List<Day> daily,
        List<Device> devices,
        List<Referrer> referrers) {

    public record Day(LocalDate date, long scans, long bots) {
    }

    public record Device(DeviceClass device, long scans) {
    }

    public record Referrer(String host, long scans) {
    }
}

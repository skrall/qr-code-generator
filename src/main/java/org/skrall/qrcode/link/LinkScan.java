package org.skrall.qrcode.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * One redirect that reached a destination, for the stats endpoint.
 *
 * <p>Holds no IP address and no user agent: only the derived device class, the bot flag and the
 * referrer's host. The link is referenced by id rather than as an association, because a scan is
 * only ever written or aggregated, never navigated.
 */
@Entity
@Table(name = "link_scan")
public class LinkScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    /** The UTC day of {@code scannedAt}, so grouping by day needs no database date function. */
    @Column(name = "scanned_on", nullable = false)
    private LocalDate scannedOn;

    // A string rather than @Enumerated: Hibernate maps an enum to a native enum type on some
    // dialects, which would fail schema validation against the varchar column.
    @Column(nullable = false, length = 16)
    private String device;

    @Column(nullable = false)
    private boolean bot;

    @Column(name = "referrer_host", length = ScanSource.MAX_HOST_LENGTH)
    private String referrerHost;

    protected LinkScan() {
        // For Hibernate.
    }

    public LinkScan(Long linkId, Instant scannedAt, ScanSource source) {
        this.linkId = linkId;
        this.scannedAt = scannedAt;
        this.scannedOn = utcDay(scannedAt);
        this.device = source.device().name();
        this.bot = source.bot();
        this.referrerHost = source.referrerHost();
    }

    static LocalDate utcDay(Instant at) {
        return LocalDate.ofInstant(at, ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getLinkId() {
        return linkId;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public LocalDate getScannedOn() {
        return scannedOn;
    }

    public DeviceClass getDevice() {
        return DeviceClass.valueOf(device);
    }

    public boolean isBot() {
        return bot;
    }

    public String getReferrerHost() {
        return referrerHost;
    }
}

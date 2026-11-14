package org.skrall.qrcode.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One stored destination and the short code that resolves to it.
 *
 * <p>The row is soft deleted rather than removed: a code may already be printed on a poster, and
 * {@code 410 Gone} is the honest answer once it stops working. A hard delete would answer 404,
 * which reads as a typo in the address.
 */
@Entity
@Table(name = "short_link")
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "hit_count", nullable = false)
    private long hitCount;

    @Column(name = "last_hit_at")
    private Instant lastHitAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "gone_url", length = 2048)
    private String goneUrl;

    @Column(name = "gone_message", length = 280)
    private String goneMessage;

    protected ShortLink() {
        // For Hibernate.
    }

    public ShortLink(String code, String targetUrl, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.targetUrl = targetUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Repoints the link without changing its code.
     *
     * <p>The code is what a printed QR image carries, so editing has to leave it alone: the
     * whole point is to move an existing code to a new destination.
     */
    public void update(String targetUrl, Instant expiresAt) {
        this.targetUrl = targetUrl;
        this.expiresAt = expiresAt;
    }

    /**
     * Sets what a scan gets once the link has expired or been deleted: a fallback destination,
     * a message for the 410 page, or neither. The caller validates both.
     */
    public void setGoneOptions(String goneUrl, String goneMessage) {
        this.goneUrl = goneUrl;
        this.goneMessage = goneMessage;
    }

    /** Counts a scan. The caller decides whether the link is still usable. */
    public void recordHit(Instant at) {
        hitCount++;
        lastHitAt = at;
    }

    public void delete(Instant at) {
        deletedAt = at;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getHitCount() {
        return hitCount;
    }

    public Instant getLastHitAt() {
        return lastHitAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getGoneUrl() {
        return goneUrl;
    }

    public String getGoneMessage() {
        return goneMessage;
    }
}

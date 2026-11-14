package org.skrall.qrcode.link;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Writes scans and aggregates them.
 *
 * <p>Every query groups on {@code scannedOn}, a stored UTC date, rather than truncating the
 * timestamp: H2 and PostgreSQL spell date truncation differently, and plain columns keep the
 * JPQL portable. The {@code (link_id, scanned_on)} index covers the filters.
 */
public interface LinkScanRepository extends JpaRepository<LinkScan, Long> {

    @Query("""
            select new org.skrall.qrcode.link.DailyScanCount(s.scannedOn, s.bot, count(s))
            from LinkScan s
            where s.linkId = :linkId and s.scannedOn >= :from and s.scannedOn <= :to
            group by s.scannedOn, s.bot
            """)
    List<DailyScanCount> countByDay(
            @Param("linkId") Long linkId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** People only: a link preview has no device worth reporting. */
    @Query("""
            select new org.skrall.qrcode.link.ScanCount(s.device, count(s))
            from LinkScan s
            where s.linkId = :linkId and s.bot = false
              and s.scannedOn >= :from and s.scannedOn <= :to
            group by s.device
            order by count(s) desc, s.device
            """)
    List<ScanCount> countByDevice(
            @Param("linkId") Long linkId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** People only, most frequent first; a null key counts the scans with no referrer. */
    @Query("""
            select new org.skrall.qrcode.link.ScanCount(s.referrerHost, count(s))
            from LinkScan s
            where s.linkId = :linkId and s.bot = false
              and s.scannedOn >= :from and s.scannedOn <= :to
            group by s.referrerHost
            order by count(s) desc, s.referrerHost nulls last
            """)
    List<ScanCount> countByReferrer(
            @Param("linkId") Long linkId, @Param("from") LocalDate from, @Param("to") LocalDate to,
            Limit limit);

    /** Drops every scan from before {@code cutoff}. Returns how many were removed. */
    @Modifying
    @Query("delete from LinkScan s where s.scannedOn < :cutoff")
    int deleteScannedBefore(@Param("cutoff") LocalDate cutoff);
}

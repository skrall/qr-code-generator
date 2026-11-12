package org.skrall.qrcode.link;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    /**
     * Finds by code, including deleted rows.
     *
     * <p>The redirect path needs the deleted row in order to answer {@code 410 Gone} rather than
     * {@code 404}; filtering it out here would erase the distinction.
     */
    Optional<ShortLink> findByCode(String code);

    boolean existsByCode(String code);

    Page<ShortLink> findByDeletedAtIsNull(Pageable pageable);

    @Query("""
            select l from ShortLink l
            where l.deletedAt is null
              and (lower(l.targetUrl) like lower(concat('%', :query, '%'))
                   or lower(l.code) like lower(concat('%', :query, '%')))
            """)
    Page<ShortLink> search(@Param("query") String query, Pageable pageable);
}

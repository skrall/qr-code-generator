package org.skrall.qrcode.design;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DesignRepository extends JpaRepository<Design, Long> {

    /** The caller's own designs and everyone's shared ones, without their styles. */
    @Query("""
            select new org.skrall.qrcode.design.DesignListing(
                d.id, d.name, d.owner, d.ownerName, d.shared, d.createdAt, d.updatedAt)
            from Design d
            where d.owner = :owner or d.shared = true
            order by lower(d.name), d.id
            """)
    List<DesignListing> findVisibleTo(@Param("owner") String owner);
}

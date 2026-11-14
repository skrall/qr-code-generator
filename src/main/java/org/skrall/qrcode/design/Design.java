package org.skrall.qrcode.design;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A named style a user saved to reuse, private to its owner unless shared.
 *
 * <p>The style is kept as the JSON the render API takes, images included, rather than as columns:
 * the style grows a field with most features, and a design should restore exactly what was saved
 * without a migration each time.
 */
@Entity
@Table(name = "design")
public class Design {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String owner;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(nullable = false)
    private boolean shared;

    @Column(nullable = false)
    private String style;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Design() {
        // For Hibernate.
    }

    public Design(String name, String owner, String ownerName, boolean shared, String style, Instant now) {
        this.name = name;
        this.owner = owner;
        this.ownerName = ownerName;
        this.shared = shared;
        this.style = style;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Renames, shares or unshares, and replaces the style when one is given. */
    void update(String name, boolean shared, String style, Instant now) {
        this.name = name;
        this.shared = shared;
        if (style != null) {
            this.style = style;
        }
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isShared() {
        return shared;
    }

    public String getStyle() {
        return style;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

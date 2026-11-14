package org.skrall.qrcode.design;

import java.time.Instant;

/**
 * A design without its style, for listing. The style can be megabytes of image data, so the list
 * query never loads it.
 */
public record DesignListing(
        Long id, String name, String owner, String ownerName, boolean shared,
        Instant createdAt, Instant updatedAt) {
}

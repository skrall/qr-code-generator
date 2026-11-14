package org.skrall.qrcode.model;

import java.time.Instant;

/**
 * A design with its style, as loaded into the editor. The fields other than {@code style} mean
 * the same as in {@link DesignSummary}.
 */
public record DesignResponse(
        Long id, String name, String owner, boolean shared, boolean mine, boolean editable,
        Instant createdAt, Instant updatedAt, QrStyle style) {
}

package org.skrall.qrcode.model;

import java.time.Instant;

/**
 * A design as it appears in the list, without its style.
 *
 * @param id        the design's id
 * @param name      what it is called
 * @param owner     who saved it, by display name; {@code null} when sign-in is off
 * @param shared    whether everyone who can sign in sees it
 * @param mine      whether the caller saved it
 * @param editable  whether the caller may rename, overwrite, share or delete it: its owner, or an
 *                  operator when it is shared
 * @param createdAt when it was first saved
 * @param updatedAt when it last changed
 */
public record DesignSummary(
        Long id, String name, String owner, boolean shared, boolean mine, boolean editable,
        Instant createdAt, Instant updatedAt) {
}

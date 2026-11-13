package org.skrall.qrcode.model;

/**
 * The shape of the three large finder patterns in the corners.
 *
 * <p>These are drawn separately from the data modules: the outer ring, the light gap and the
 * centre have to stay concentric, and a per-module shape cannot express that.
 */
public enum FinderShape {
    SQUARE,
    ROUNDED,
    CIRCLE,
    /** Rounded on two opposite corners, square on the other two. */
    LEAF,
    /** A square rotated a quarter turn, inscribed in the finder's footprint. */
    DIAMOND,
    /**
     * The standard square rings filled with the supplied artwork.
     *
     * <p>The ring and centre stay in place; only the colour inside them comes from the image.
     * That keeps the 1:1:3:1:1 structure a reader looks for. Without artwork this falls back to
     * {@link #SQUARE}.
     */
    CUSTOM
}

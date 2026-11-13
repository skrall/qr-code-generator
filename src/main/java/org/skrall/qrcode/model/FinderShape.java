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
    DIAMOND
}

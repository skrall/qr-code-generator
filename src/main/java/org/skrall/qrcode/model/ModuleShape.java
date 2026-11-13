package org.skrall.qrcode.model;

/**
 * The shape of a single data module.
 */
public enum ModuleShape {
    /** Filled squares, the classic look. */
    SQUARE,
    /** Squares with softened corners. */
    ROUNDED,
    /** Circles with a small gap around them. */
    DOTS,
    /** Four-point stars whose tips meet the edges of the module cell. */
    STAR,
    /**
     * Rounded modules merged into their neighbours, so a run of dark modules becomes one
     * continuous bar rather than a chain of separate shapes.
     */
    LIQUID
}

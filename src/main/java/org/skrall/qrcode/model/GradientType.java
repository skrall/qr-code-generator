package org.skrall.qrcode.model;

/** How a gradient's colours are laid out across the code. */
public enum GradientType {
    /** Along a straight line through the centre, at {@link QrGradient#angle()}. */
    LINEAR,
    /** Outward from the centre to the corners. */
    RADIAL
}

package org.skrall.qrcode.model;

/**
 * The error-correction level, in the terms a caller sees rather than ZXing's letters.
 *
 * <p>A higher level survives more damage but spends more of the symbol on redundancy, which
 * leaves less room for data. The UI nudges towards {@link #HIGH} whenever a logo is embedded,
 * because the logo covers modules that then have to be reconstructed by the decoder.
 */
public enum ErrorCorrection {
    LOW,
    MEDIUM,
    QUARTILE,
    HIGH
}

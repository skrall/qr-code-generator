package org.skrall.qrcode.model;

/** How confident the scan check is that phones will read the code. */
public enum ScanVerdict {
    /** Decoded as rendered and in every simulated photo. */
    PASS,
    /** Decoded, but only with extra effort or not in every simulated photo. */
    MARGINAL,
    /** Did not decode to the payload at all. */
    FAIL
}

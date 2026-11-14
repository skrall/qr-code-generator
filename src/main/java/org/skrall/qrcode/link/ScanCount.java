package org.skrall.qrcode.link;

/**
 * Scans per value of one attribute: a device class, or a referrer host (null for none). Built by
 * Hibernate from a JPQL constructor expression; registered in {@code ApiModelRuntimeHints}.
 */
public record ScanCount(String key, long scans) {
}

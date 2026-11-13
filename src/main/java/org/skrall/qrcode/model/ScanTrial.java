package org.skrall.qrcode.model;

/**
 * One attempt to read the rendered code.
 *
 * @param name        a stable identifier: {@code rendered}, {@code small} or {@code blurred}
 * @param description what the attempt simulates, for display
 * @param decoded     whether the reader recovered exactly the payload
 */
public record ScanTrial(String name, String description, boolean decoded) {
}

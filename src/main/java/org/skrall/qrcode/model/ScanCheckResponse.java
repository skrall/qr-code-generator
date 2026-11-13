package org.skrall.qrcode.model;

import java.util.List;

/**
 * The result of reading a rendered code back.
 *
 * @param verdict the overall result
 * @param version the QR version the payload needed, 1–40
 * @param modules the symbol's side in modules, without the quiet zone
 * @param trials  each attempt to read it, in the order made
 * @param hints   changes likely to help, most likely first; empty when the verdict is a pass
 */
public record ScanCheckResponse(
        ScanVerdict verdict, int version, int modules, List<ScanTrial> trials, List<String> hints) {
}

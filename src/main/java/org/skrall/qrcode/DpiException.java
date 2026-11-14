package org.skrall.qrcode;

/**
 * Thrown when a requested print resolution is outside the supported range.
 */
public class DpiException extends RuntimeException {

    public DpiException(String message) {
        super(message);
    }
}

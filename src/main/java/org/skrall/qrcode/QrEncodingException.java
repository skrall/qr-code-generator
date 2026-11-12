package org.skrall.qrcode;

/**
 * Thrown when the payload does not fit in a QR code at the requested error-correction level.
 */
public class QrEncodingException extends RuntimeException {

    public QrEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}

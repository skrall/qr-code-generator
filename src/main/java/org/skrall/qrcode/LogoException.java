package org.skrall.qrcode;

/**
 * Thrown when the logo cannot be used: it is not a data URL, not base64, not an image, or too
 * large to decode safely.
 */
public class LogoException extends RuntimeException {

    public LogoException(String message) {
        super(message);
    }
}

package org.skrall.qrcode;

/**
 * Thrown when the background image cannot be used: it is not a data URL, not base64, not an
 * image, or too large to decode safely.
 */
public class BackgroundImageException extends RuntimeException {

    public BackgroundImageException(String message) {
        super(message);
    }
}

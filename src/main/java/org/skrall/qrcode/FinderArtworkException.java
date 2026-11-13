package org.skrall.qrcode;

/**
 * Thrown when the custom finder artwork cannot be used: it is not a data URL, not base64, not an
 * image, or too large to decode safely.
 */
public class FinderArtworkException extends RuntimeException {

    public FinderArtworkException(String message) {
        super(message);
    }
}

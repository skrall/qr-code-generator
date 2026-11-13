package org.skrall.qrcode;

/**
 * Thrown when a frame cannot be drawn: its text uses characters the bundled font lacks, it
 * crowds the code's quiet zone, or the framed image would be too large.
 */
public class FrameException extends RuntimeException {

    public FrameException(String message) {
        super(message);
    }
}

package org.skrall.qrcode;

/**
 * Thrown when a gradient would make part of the code unreadable: a stop is too close to the
 * background, or the stops sit on opposite sides of it.
 */
public class GradientException extends RuntimeException {

    public GradientException(String message) {
        super(message);
    }
}

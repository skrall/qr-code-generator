package org.skrall.qrcode.design;

/**
 * No design with this id is visible to the caller. A private design of someone else's answers
 * the same as one that does not exist, so its existence is not disclosed.
 */
public class DesignNotFoundException extends RuntimeException {

    public DesignNotFoundException(long id) {
        super("No design " + id + " is visible to you.");
    }
}

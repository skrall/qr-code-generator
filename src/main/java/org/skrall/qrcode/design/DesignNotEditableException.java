package org.skrall.qrcode.design;

/** The caller can see a design but may not change it. */
public class DesignNotEditableException extends RuntimeException {

    public DesignNotEditableException() {
        super("Only the design's owner, or an operator for a shared design, can change it.");
    }
}

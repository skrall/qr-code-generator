package org.skrall.qrcode.link;

/**
 * A request to create or resolve a link that cannot be honoured.
 *
 * <p>Separate from the validation constraints because the checks that matter here are about the
 * URL's structure — scheme, host, credentials — which Bean Validation cannot express without a
 * pattern that would be both unreadable and incomplete.
 */
public class InvalidLinkException extends RuntimeException {

    public InvalidLinkException(String message) {
        super(message);
    }
}

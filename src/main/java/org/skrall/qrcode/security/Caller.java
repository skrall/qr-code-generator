package org.skrall.qrcode.security;

/**
 * Who is making the current request.
 *
 * @param id       a stable identifier to record ownership by: the principal name, which for
 *                 OIDC is the subject. Empty when sign-in is off, so everything is one owner's.
 * @param name     the name a person recognises, for display; {@code null} when sign-in is off
 * @param operator whether the caller holds the OPERATOR role (always, when sign-in is off)
 */
public record Caller(String id, String name, boolean operator) {

    static final Caller ANONYMOUS = new Caller("", null, true);
}

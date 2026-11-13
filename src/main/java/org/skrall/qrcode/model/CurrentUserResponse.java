package org.skrall.qrcode.model;

/**
 * Who the UI is talking to, so it can hide what the user may not do.
 *
 * @param name     the signed-in user's name, or {@code null} when security is off
 * @param operator whether the user may create, edit and delete short links
 */
public record CurrentUserResponse(String name, boolean operator) {
}

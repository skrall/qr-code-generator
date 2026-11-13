package org.skrall.qrcode.web;

import io.swagger.v3.oas.annotations.Operation;
import org.skrall.qrcode.model.CurrentUserResponse;
import org.skrall.qrcode.security.AppSecurityProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the UI who is signed in and whether they hold the OPERATOR role.
 *
 * <p>The server still enforces the role on every change; this only lets the UI hide buttons that
 * would answer 403, rather than offering them and failing.
 */
@RestController
public class CurrentUserController {

    private final AppSecurityProperties properties;

    public CurrentUserController(AppSecurityProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/me")
    @Operation(summary = "The signed-in user", description = """
            The user's name and whether they may create, edit and delete short links. With
            security off, the name is null and every action is allowed.
            """)
    public CurrentUserResponse me() {
        if (properties.mode() == AppSecurityProperties.Mode.NONE) {
            return new CurrentUserResponse(null, true);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean operator = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_OPERATOR"::equals);
        return new CurrentUserResponse(displayName(authentication), operator);
    }

    /** An OIDC subject is an opaque id; the name a person recognises is in the claims. */
    private static String displayName(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser user) {
            if (user.getPreferredUsername() != null) {
                return user.getPreferredUsername();
            }
            if (user.getEmail() != null) {
                return user.getEmail();
            }
        }
        return authentication.getName();
    }
}

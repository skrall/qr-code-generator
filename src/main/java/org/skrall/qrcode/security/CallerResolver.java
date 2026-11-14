package org.skrall.qrcode.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link Caller} of the current request from the security context, the same way in
 * every sign-in mode.
 */
@Component
public class CallerResolver {

    private final AppSecurityProperties properties;

    public CallerResolver(AppSecurityProperties properties) {
        this.properties = properties;
    }

    public Caller current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (properties.mode() == AppSecurityProperties.Mode.NONE || authentication == null) {
            return Caller.ANONYMOUS;
        }
        boolean operator = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_OPERATOR"::equals);
        return new Caller(authentication.getName(), displayName(authentication), operator);
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

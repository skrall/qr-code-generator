package org.skrall.qrcode.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;

/**
 * Turns the group-membership claim an identity provider hands over into the application roles.
 *
 * <p>The vendors differ in which claim carries the membership: Okta defaults to {@code groups},
 * Entra ID to {@code roles} (or {@code groups}, which needs admin consent and caps at 150). The
 * claim is configurable via {@code app.security.roles-claim} and the operator group
 * names via {@code app.security.operator-groups}, so the vendor difference lives in
 * configuration rather than code.
 *
 * <p>The claim shape is not always a JSON array: a single value arrives as a plain string, and
 * Entra carries {@code roles} as well as {@code wids}. Both shapes are normalised before the
 * roles are decided; the decision itself is {@link GroupRolesMapper}'s, so a trusted-header
 * identity with the same groups gets the same authorities.
 */
public final class OidcRolesMapper {

    private final String rolesClaim;
    private final GroupRolesMapper roles;

    public OidcRolesMapper(String rolesClaim, List<String> operatorGroups) {
        this(rolesClaim, new GroupRolesMapper(operatorGroups));
    }

    OidcRolesMapper(String rolesClaim, GroupRolesMapper roles) {
        this.rolesClaim = rolesClaim;
        this.roles = roles;
    }

    public Set<GrantedAuthority> authoritiesFrom(Map<String, Object> claims) {
        return roles.authoritiesFor(readGroups(claims));
    }

    private Set<String> readGroups(Map<String, Object> claims) {
        Set<String> groups = new LinkedHashSet<>();
        Object value = claims.get(rolesClaim);
        if (value instanceof String single) {
            groups.add(single);
        } else if (value instanceof String[] array) {
            for (String entry : array) {
                groups.add(entry);
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null) {
                    groups.add(entry.toString());
                }
            }
        }
        return groups;
    }
}
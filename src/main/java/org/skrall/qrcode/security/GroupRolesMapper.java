package org.skrall.qrcode.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Turns a list of group names into the application roles, shared by the identity-provider modes
 * so that OIDC and trusted-header get identical authorities.
 *
 * <p>Which group names carry the OPERATOR role is fixed per deployment by
 * {@code app.security.operator-groups} and matched case-insensitively, the spelling
 * vendors make no promises about. Everyone authenticated is at least a VIEWER (using the generator
 * presupposes being admitted), and an OPERATOR also holds VIEWER, matching the Basic identity
 * model. With no operator groups configured nobody is ever an OPERATOR.
 */
public final class GroupRolesMapper {

    private final Set<String> operatorGroups;

    public GroupRolesMapper(List<String> operatorGroups) {
        Set<String> groups = new LinkedHashSet<>();
        for (String group : operatorGroups) {
            String trimmed = group.trim();
            if (!trimmed.isEmpty()) {
                groups.add(trimmed.toLowerCase());
            }
        }
        this.operatorGroups = Set.copyOf(groups);
    }

    /**
     * The roles an identity holding {@code groups} may use.
     *
     * <p>An {@link Iterable} is accepted so both a claim's array and a header's comma-separated
     * list feed the same decision; the caller normalises the shape, this class only decides.
     */
    public Set<GrantedAuthority> authoritiesFor(Iterable<String> groups) {
        boolean operator = false;
        if (!operatorGroups.isEmpty()) {
            for (String group : groups) {
                if (group != null && operatorGroups.contains(group.trim().toLowerCase())) {
                    operator = true;
                    break;
                }
            }
        }
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
        if (operator) {
            authorities.add(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        }
        return authorities;
    }
}
package org.skrall.qrcode.security;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

/**
 * Authenticates a request against the identity the configured reverse proxy set in headers.
 *
 * <p>Extends {@link RequestHeaderAuthenticationFilter} (the pre-auth package) with the two
 * things this deployment needs beyond the stock filter: the identity header is trimmed and a
 * blank value treated as missing, and the groups header feeds the same
 * {@link GroupRolesMapper} the OIDC mode uses, so a proxy asserting groups yields identical
 * authorities to an identity provider asserting the same claim.
 *
 * <p>The filter does not decide where the request came from; that is the
 * {@link TrustedProxyRequestMatcher}'s job on the chain, and a request that matches the
 * trusted-header chain at all is one the operator has already said may be believed.
 */
public final class TrustedHeaderAuthenticationFilter extends RequestHeaderAuthenticationFilter {

    private final String principalHeader;
    private final String groupsHeader;
    private final GroupRolesMapper rolesMapper;

    public TrustedHeaderAuthenticationFilter(String principalHeader, String groupsHeader,
            GroupRolesMapper rolesMapper) {
        setPrincipalRequestHeader(principalHeader);
        // A missing identity header is a 401 through the authorization rule, not a server error;
        // only a header that is present and blank is treated as missing too, so whitespace cannot
        // impersonate an identity.
        setExceptionIfHeaderMissing(false);
        setContinueFilterChainOnUnsuccessfulAuthentication(false);
        // The chain matcher has already vouched for the source; an authentication manager that
        // is just the identity (decorated with the mapped roles) is all the flow needs. The
        // credentials slot carries the parsed groups the mapper needs, since the manager sees no
        // request.
        setAuthenticationManager(authentication -> new PreAuthenticatedAuthenticationToken(
                authentication.getPrincipal(), null,
                rolesMapper.authoritiesFor(groupsOf(authentication.getCredentials()))));
        this.principalHeader = principalHeader;
        this.groupsHeader = groupsHeader;
        this.rolesMapper = rolesMapper;
    }

    @Override
    protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
        String identity = request.getHeader(principalHeader);
        return identity == null || identity.isBlank() ? null : identity.trim();
    }

    /**
     * The comma-separated groups header, pre-split so the authentication manager can map them
     * without the request.
     *
     * <p>None of the spelling variants railroad a separator; oauth2-proxy joins groups with
     * commas and trims, and a single group needs no separator at all. A missing or empty header
     * means no groups, which the mapper reads as a plain VIEWER rather than an operator.
     */
    @Override
    protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
        if (groupsHeader.isBlank()) {
            return List.of();
        }
        String groups = request.getHeader(groupsHeader);
        if (groups == null || groups.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String group : groups.split(",")) {
            String trimmed = group.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return List.copyOf(names);
    }

    private static List<String> groupsOf(Object credentials) {
        if (!(credentials instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
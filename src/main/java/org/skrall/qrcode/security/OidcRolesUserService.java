package org.skrall.qrcode.security;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * The {@link OidcUserService} that stamps the role claims on a logged-in user.
 *
 * <p>The stock service loads the id token and userinfo; this one folds in the
 * {@link OidcRolesMapper} result (ROLE_VIEWER always, ROLE_OPERATOR when a configured group is
 * present) so the rest of the application sees the same roles as Basic gives its users. The
 * user's own authorities are kept, so a change of claim name or groups never silently removes
 * the identity that WebSecurity recognises as authenticated.
 */
final class OidcRolesUserService extends OidcUserService {

    private final OidcRolesMapper mapper;

    OidcRolesUserService(OidcRolesMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(userRequest);
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(user.getAuthorities());
        authorities.addAll(mapper.authoritiesFrom(user.getClaims()));
        OidcIdToken idToken = user.getIdToken();
        OidcUserInfo userInfo = user.getUserInfo();
        return userInfo == null
                ? new DefaultOidcUser(authorities, idToken)
                : new DefaultOidcUser(authorities, idToken, userInfo);
    }
}
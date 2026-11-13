package org.skrall.qrcode.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for request authentication, modelled on mailoverlord's.
 *
 * <p>{@code mode} picks which filter chain guards the application: {@code none} keeps the
 * pre-security behaviour and is what the general test suite runs under, {@code basic} requires
 * HTTP Basic, {@code oidc} signs browsers in against an identity provider through the
 * authorization code flow, and {@code header} believes the identity headers a trusted reverse
 * proxy sets instead of holding any credential of its own.
 *
 * <p>{@code operatorUsers} and {@code viewerUsers} name identities by user name, sharing one
 * {@code spring.security.user.password}. OPERATOR may create, edit and delete short links;
 * VIEWER may render codes and list links. The {@code spring.security.user.*} identity is always
 * an OPERATOR. Under {@code oidc} the two lists are unused: {@code operatorGroups} and
 * {@code rolesClaim} say which identity-provider groups get the OPERATOR role. Under
 * {@code header}, {@code operatorGroups} picks the group names (from {@code groupsHeader}) that
 * get it too, with the same case-insensitive matching.
 *
 * <p>{@code header} mode must never trust the identity header on a connection that did not come
 * from a configured proxy; {@code trustedProxies} is that allowlist, and the record refuses to
 * be constructed for {@code header} without it, so misconfiguration fails the app at startup
 * rather than silently accepting forged headers.
 *
 * @param mode           which authentication mechanism to enforce
 * @param operatorUsers  the user names that may change short links
 * @param viewerUsers    the user names that may only render and read
 * @param rolesClaim     the OIDC claim that carries group membership
 * @param operatorGroups the group names that may change short links (OIDC and header modes)
 * @param header         the request header carrying the authenticated identity ({@code header} mode)
 * @param groupsHeader   the request header carrying comma-separated group membership ({@code header} mode)
 * @param trustedProxies the CIDR blocks whose connections may present the identity headers
 */
@ConfigurationProperties("app.security")
public record AppSecurityProperties(
        @DefaultValue("basic") Mode mode,
        List<String> operatorUsers,
        List<String> viewerUsers,
        @DefaultValue("groups") String rolesClaim,
        List<String> operatorGroups,
        @DefaultValue("") String header,
        @DefaultValue("") String groupsHeader,
        List<String> trustedProxies) {

    public AppSecurityProperties {
        operatorUsers = operatorUsers == null ? List.of() : List.copyOf(operatorUsers);
        viewerUsers = viewerUsers == null ? List.of() : List.copyOf(viewerUsers);
        operatorGroups = operatorGroups == null ? List.of() : List.copyOf(operatorGroups);
        header = header == null ? "" : header;
        groupsHeader = groupsHeader == null ? "" : groupsHeader;
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        if (mode == Mode.HEADER) {
            if (header.isBlank()) {
                throw new IllegalArgumentException(
                        "app.security.header must name the identity header when mode is header");
            }
            if (trustedProxies.isEmpty()) {
                throw new IllegalArgumentException(
                        "app.security.trusted-proxies must name at least one CIDR when mode is "
                                + "header; the identity header is trusted from nowhere");
            }
        }
    }

    public enum Mode {
        NONE,
        BASIC,
        OIDC,
        HEADER
    }
}

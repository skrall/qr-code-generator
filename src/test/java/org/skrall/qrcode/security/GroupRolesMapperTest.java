package org.skrall.qrcode.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The group decision shared by OIDC and trusted-header modes: same groups in, same authorities
 * out, whichever of the two mechanisms carried them.
 */
class GroupRolesMapperTest {

    private static final GroupRolesMapper MAPPER =
            new GroupRolesMapper(List.of("QR-Operators"));

    @Test
    void anOperatorGroup_yieldsOperator() {
        assertThat(MAPPER.authoritiesFor(List.of("qr-operators", "other")))
                .containsExactlyInAnyOrder(
                        new SimpleGrantedAuthority("ROLE_OPERATOR"),
                        new SimpleGrantedAuthority("ROLE_VIEWER"));
    }

    @Test
    void aNonOperatorGroup_leavesOnlyViewer() {
        assertThat(MAPPER.authoritiesFor(List.of("everyone", "developers")))
                .containsExactly(new SimpleGrantedAuthority("ROLE_VIEWER"));
    }

    @Test
    void aGroupIsMatchedCaseInsensitively() {
        assertThat(MAPPER.authoritiesFor(List.of("QR-OPERATORS")))
                .contains(new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertThat(MAPPER.authoritiesFor(List.of(" qr-operators ")))
                .contains(new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    @Test
    void noGroupsAtAll_leavesOnlyViewer() {
        assertThat(MAPPER.authoritiesFor(List.of()))
                .containsExactly(new SimpleGrantedAuthority("ROLE_VIEWER"));
    }

    @Test
    void noOperatorGroupsConfigured_meansNobodyIsAnOperator() {
        GroupRolesMapper unrestricted = new GroupRolesMapper(List.of());
        assertThat(unrestricted.authoritiesFor(List.of("anything")))
                .containsExactly(new SimpleGrantedAuthority("ROLE_VIEWER"));
    }

    @Test
    void theSetIsNotInfluencedByOrderOrDuplicates() {
        Set<?> first = MAPPER.authoritiesFor(List.of("other", "qr-operators"));
        Set<?> second = MAPPER.authoritiesFor(List.of("qr-operators", "qr-operators"));
        assertThat(first).isEqualTo(second);
    }
}
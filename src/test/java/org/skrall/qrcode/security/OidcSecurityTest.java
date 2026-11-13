package org.skrall.qrcode.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.link.ShortLink;
import org.skrall.qrcode.link.ShortLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The OIDC chain. Roles come from the {@code oidcLogin()} post-processor rather than a real code
 * exchange, which no test should make; the claim-to-role mapping is unit-tested in
 * {@link OidcRolesMapperTest}.
 *
 * <p>Most POSTs here add {@code csrf()}, but {@link #theSpaCsrfRoundTripIsAccepted} does not: it
 * echoes the cookie the way the UI does, which is the only way to show the real negotiation
 * works rather than the test helper's shortcut.
 */
@SpringBootTest(properties = {
        "app.security.mode=oidc",
        "app.security.roles-claim=groups",
        "app.security.operator-groups=qr-operators",
        // The entry point reads the registration ids to build the provider login URL. The
        // repository below replaces the one these would build, so the issuer is never fetched.
        "spring.security.oauth2.client.registration.okta.client-id=test-client",
        "spring.security.oauth2.client.registration.okta.client-secret=test-secret",
        "spring.security.oauth2.client.registration.okta.provider=okta",
        "spring.security.oauth2.client.provider.okta.issuer-uri=https://example.okta.com/oauth2/default"})
@AutoConfigureMockMvc
class OidcSecurityTest {

    private static final String LINK = """
            {"url":"https://example.com/destination"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService links;

    @TestConfiguration
    static class OidcClients {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("okta")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("https://example.okta.com/oauth2/default/v1/authorize")
                    .tokenUri("https://example.okta.com/oauth2/default/v1/token")
                    .jwkSetUri("https://example.okta.com/oauth2/default/v1/keys")
                    .userNameAttributeName("sub")
                    .build());
        }
    }

    private static OidcLoginRequestPostProcessor viewer() {
        return oidcLogin()
                .idToken(token -> token.claim("preferred_username", "vera"))
                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"));
    }

    private static OidcLoginRequestPostProcessor operator() {
        return oidcLogin()
                .idToken(token -> token.claim("preferred_username", "olga"))
                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"),
                        new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    @Test
    void anAnonymousApiCallNamesTheProviderLogin() throws Exception {
        mockMvc.perform(get("/api/links"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.login-url").value("/oauth2/authorization/okta"));
    }

    @Test
    void anAnonymousPageIsSentToTheProvider() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/okta"));
    }

    @Test
    void theRedirectsAnswerAnyone() throws Exception {
        ShortLink link = links.create("https://example.com/oidc", null);

        mockMvc.perform(get("/r/{code}", link.getCode())).andExpect(status().isFound());
    }

    @Test
    void theCurrentUserIsNamedFromTheClaims() throws Exception {
        mockMvc.perform(get("/api/me").with(viewer()))
                .andExpect(jsonPath("$.name").value("vera"))
                .andExpect(jsonPath("$.operator").value(false));
        mockMvc.perform(get("/api/me").with(operator()))
                .andExpect(jsonPath("$.name").value("olga"))
                .andExpect(jsonPath("$.operator").value(true));
    }

    @Test
    void aViewerCannotCreateALink() throws Exception {
        mockMvc.perform(post("/api/links").with(viewer()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(LINK))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("That needs the operator role."));
    }

    @Test
    void aChangeWithoutTheCsrfTokenIsRefusedAndSaysWhy() throws Exception {
        mockMvc.perform(post("/api/links").with(operator())
                        .contentType(MediaType.APPLICATION_JSON).content(LINK))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "The request was missing its CSRF token. Reload the page and try again."));
    }

    @Test
    void theSpaCsrfRoundTripIsAccepted() throws Exception {
        // Any response carries the token cookie; the UI reads it and echoes it in the header.
        MvcResult first = mockMvc.perform(get("/api/me").with(operator())).andReturn();
        Cookie token = first.getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        assertThat(token.isHttpOnly()).isFalse();

        mockMvc.perform(post("/api/links").with(operator())
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(LINK))
                .andExpect(status().isCreated());
    }
}

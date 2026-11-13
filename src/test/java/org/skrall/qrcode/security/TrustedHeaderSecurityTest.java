package org.skrall.qrcode.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.skrall.qrcode.link.ShortLink;
import org.skrall.qrcode.link.ShortLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The trusted-header chains. Each request states where its connection came from with
 * {@code remoteAddress(...)}, and {@code trusted-proxies} says which of those are believed.
 *
 * <p>{@link #aHeaderFromOutsideTheTrustedProxiesIsRejected} is the test that has to exist: the
 * same header that signs a user in from the proxy must be worthless from anywhere else.
 */
@SpringBootTest(properties = {
        "app.security.mode=header",
        "app.security.header=X-Remote-User",
        "app.security.groups-header=X-Forwarded-Groups",
        "app.security.operator-groups=qr-operators",
        "app.security.trusted-proxies=10.0.0.0/8,127.0.0.1/32"})
@AutoConfigureMockMvc
class TrustedHeaderSecurityTest {

    private static final String TRUSTED = "10.0.0.6";
    private static final String OUTSIDER = "172.16.0.5";
    private static final String LINK = """
            {"url":"https://example.com/destination"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService links;

    @Test
    void anIdentityFromATrustedProxyIsSignedIn() throws Exception {
        mockMvc.perform(get("/api/me").remoteAddress(TRUSTED).header("X-Remote-User", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("alice"))
                .andExpect(jsonPath("$.operator").value(false));
    }

    @Test
    void anOperatorGroupMayChangeLinksAndOthersMayNot() throws Exception {
        mockMvc.perform(post("/api/links").remoteAddress(TRUSTED)
                        .header("X-Remote-User", "alice")
                        .header("X-Forwarded-Groups", "everyone, QR-Operators")
                        .contentType(MediaType.APPLICATION_JSON).content(LINK))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/links").remoteAddress(TRUSTED)
                        .header("X-Remote-User", "bob")
                        .header("X-Forwarded-Groups", "everyone")
                        .contentType(MediaType.APPLICATION_JSON).content(LINK))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTrustedRequestWithoutTheIdentityHeaderIsRefused() throws Exception {
        mockMvc.perform(get("/api/links").remoteAddress(TRUSTED).header("X-Remote-User", "  "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aHeaderFromOutsideTheTrustedProxiesIsRejected() throws Exception {
        mockMvc.perform(get("/api/links").remoteAddress(OUTSIDER)
                        .header("X-Remote-User", "alice")
                        .header("X-Forwarded-Groups", "qr-operators"))
                .andExpect(status().isUnauthorized())
                // The proxy owns sign-in: no login page to send the browser to, no Basic prompt.
                .andExpect(jsonPath("$.login-url").doesNotExist())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void aScanThatBypassesTheProxyStillRedirects() throws Exception {
        ShortLink link = links.create("https://example.com/scanned", null);

        mockMvc.perform(get("/r/{code}", link.getCode()).remoteAddress(OUTSIDER))
                .andExpect(status().isFound());
    }
}

package org.skrall.qrcode.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
 * The Basic chain against the whole application: who may reach what, and what a refusal says.
 *
 * <p>The tests sign in with real Basic credentials rather than {@code @WithMockUser}: the point
 * is that the documented {@code spring.security.user.*} identity is OPERATOR while a name in
 * {@code viewer-users} is not, which only a real login through the configured
 * {@code UserDetailsService} can show.
 */
@SpringBootTest(properties = {"app.security.mode=basic", "app.security.viewer-users=reader"})
@AutoConfigureMockMvc
class BasicSecurityTest {

    private static final String PASSWORD = "test-password";
    private static final String RENDER = """
            {"data":"https://example.com","style":{"size":256}}""";
    private static final String LINK = """
            {"url":"https://example.com/destination"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService links;

    @Test
    void anAnonymousApiCallIsAProblemNamingTheLoginPage() throws Exception {
        mockMvc.perform(get("/api/links"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"qr-code-generator\""))
                .andExpect(jsonPath("$.login-url").value("/login"));
    }

    @Test
    void theUiAndRenderingNeedASignIn() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/login")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/qr-codes").contentType(MediaType.APPLICATION_JSON).content(RENDER))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/qr-codes/check").contentType(MediaType.APPLICATION_JSON).content(RENDER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongPasswordIsRefused() throws Exception {
        mockMvc.perform(get("/api/links").with(httpBasic("operator", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theRedirectsAnswerAnyone() throws Exception {
        ShortLink live = links.create("https://example.com/live", null);
        ShortLink removed = links.create("https://example.com/removed", null);
        links.delete(removed.getCode());

        mockMvc.perform(get("/r/{code}", live.getCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/live"));
        mockMvc.perform(get("/r/{code}", removed.getCode())).andExpect(status().isGone());
        mockMvc.perform(get("/r/{code}", "nosuchcode")).andExpect(status().isNotFound());
    }

    @Test
    void healthAndTheApiDocumentAnswerAnyone() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void aViewerRendersChecksAndListsButCannotChangeLinks() throws Exception {
        ShortLink link = links.create("https://example.com/viewer", null);

        mockMvc.perform(post("/api/qr-codes").with(httpBasic("reader", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(RENDER))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/qr-codes/check").with(httpBasic("reader", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(RENDER))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/links").with(httpBasic("reader", PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/links").with(httpBasic("reader", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(LINK))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("That needs the operator role."));
        mockMvc.perform(put("/api/links/{code}", link.getCode()).with(httpBasic("reader", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(LINK))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/links/{code}", link.getCode()).with(httpBasic("reader", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theOperatorChangesLinks() throws Exception {
        mockMvc.perform(post("/api/links").with(httpBasic("operator", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(LINK))
                .andExpect(status().isCreated());

        ShortLink link = links.create("https://example.com/operator", null);
        mockMvc.perform(delete("/api/links/{code}", link.getCode()).with(httpBasic("operator", PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    void theCurrentUserSaysWhoAndWhetherTheyMayChangeLinks() throws Exception {
        mockMvc.perform(get("/api/me").with(httpBasic("operator", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("operator"))
                .andExpect(jsonPath("$.operator").value(true));
        mockMvc.perform(get("/api/me").with(httpBasic("reader", PASSWORD)))
                .andExpect(jsonPath("$.name").value("reader"))
                .andExpect(jsonPath("$.operator").value(false));
    }
}

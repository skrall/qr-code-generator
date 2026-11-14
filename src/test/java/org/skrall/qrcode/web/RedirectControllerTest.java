package org.skrall.qrcode.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.link.ShortLink;
import org.skrall.qrcode.link.ShortLinkRepository;
import org.skrall.qrcode.link.ShortLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkRepository repository;

    @Autowired
    private ShortLinkService service;

    @BeforeEach
    void clearLinks() {
        repository.deleteAll();
    }

    @Test
    void redirectsToTheStoredDestination() throws Exception {
        ShortLink link = service.create("https://example.com/target?x=1", null);

        mockMvc.perform(get("/r/{code}", link.getCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target?x=1"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex"));
    }

    @Test
    void countsEveryScan() throws Exception {
        ShortLink link = service.create("https://example.com", null);

        mockMvc.perform(get("/r/{code}", link.getCode())).andExpect(status().isFound());
        mockMvc.perform(get("/r/{code}", link.getCode())).andExpect(status().isFound());

        ShortLink reloaded = repository.findByCode(link.getCode()).orElseThrow();
        assertThat(reloaded.getHitCount()).isEqualTo(2);
        assertThat(reloaded.getLastHitAt()).isNotNull();
    }

    @Test
    void anUnknownCodeIsNotFound() throws Exception {
        mockMvc.perform(get("/r/{code}", "doesNotExist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("does not exist")));
    }

    @Test
    void anExpiredCodeIsGone() throws Exception {
        repository.save(new ShortLink("expired01", "https://example.com",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)));

        mockMvc.perform(get("/r/{code}", "expired01"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("expired")));
    }

    @Test
    void aDeletedCodeIsGoneRatherThanMissing() throws Exception {
        ShortLink link = service.create("https://example.com", null);

        assertThat(service.delete(link.getCode())).isTrue();

        mockMvc.perform(get("/r/{code}", link.getCode()))
                .andExpect(status().isGone());
    }

    @Test
    void anExpiredCodeIsNotCounted() throws Exception {
        repository.save(new ShortLink("expired02", "https://example.com",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)));

        mockMvc.perform(get("/r/{code}", "expired02")).andExpect(status().isGone());

        ShortLink reloaded = repository.findByCode("expired02").orElseThrow();
        assertThat(reloaded.getHitCount()).isZero();
        assertThat(reloaded.getLastHitAt()).isNull();
    }

    @Test
    void anExpiredLinkShowsItsOwnMessage() throws Exception {
        expired("expired03", null, "The spring sale is over.\nSee you in autumn!");

        mockMvc.perform(get("/r/{code}", "expired03"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(
                        "<p>The spring sale is over.\nSee you in autumn!</p>")))
                .andExpect(content().string(not(containsString("It has expired or been removed"))));
    }

    @Test
    void theMessageIsEscapedBecauseItIsUserInput() throws Exception {
        expired("expired04", null,
                "<script>alert(1)</script><img src=x onerror=\"alert(2)\"> &amp; 'quoted'");

        mockMvc.perform(get("/r/{code}", "expired04"))
                .andExpect(status().isGone())
                .andExpect(content().string(allOf(
                        containsString("&lt;script&gt;alert(1)&lt;/script&gt;"),
                        containsString("&lt;img src=x onerror=&quot;alert(2)&quot;&gt;"),
                        containsString("&amp;amp; &#39;quoted&#39;"),
                        not(containsString("<script>")),
                        not(containsString("<img")))));
    }

    @Test
    void aNonAsciiMessageSurvivesTheResponseEncoding() throws Exception {
        expired("expired05", null, "Danke sch\u00f6n \u2014 \ud83d\udc4b");

        mockMvc.perform(get("/r/{code}", "expired05"))
                .andExpect(content().contentTypeCompatibleWith("text/html;charset=UTF-8"))
                .andExpect(content().encoding(java.nio.charset.StandardCharsets.UTF_8))
                .andExpect(content().string(containsString("Danke sch\u00f6n \u2014 \ud83d\udc4b")));
    }

    @Test
    void theGonePageNeverRevealsTheOldDestination() throws Exception {
        expired("expired06", null, null);

        mockMvc.perform(get("/r/{code}", "expired06"))
                .andExpect(status().isGone())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(containsString("no longer active")))
                .andExpect(content().string(not(containsString("example.com"))));
    }

    @Test
    void aDeletedLinkWithAFallbackRedirectsThere() throws Exception {
        ShortLink link = service.create("https://example.com/old", null,
                "https://example.com/new-campaign", "ignored while there is a fallback");
        service.delete(link.getCode());

        mockMvc.perform(get("/r/{code}", link.getCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/new-campaign"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex"));

        ShortLink reloaded = repository.findByCode(link.getCode()).orElseThrow();
        assertThat(reloaded.getHitCount()).isZero();
    }

    @Test
    void anExpiredLinkWithAFallbackRedirectsThere() throws Exception {
        expired("expired07", "https://example.com/fallback", null);

        mockMvc.perform(get("/r/{code}", "expired07"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/fallback"));
    }

    @Test
    void aLiveLinkIgnoresItsGoneOptions() throws Exception {
        ShortLink link = service.create("https://example.com/live", null,
                "https://example.com/fallback", "gone");

        mockMvc.perform(get("/r/{code}", link.getCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/live"));
    }

    @Test
    void bothPagesAreLockedDown() throws Exception {
        expired("expired08", null, "bye");

        for (String code : new String[] {"expired08", "doesNotExist"}) {
            mockMvc.perform(get("/r/{code}", code))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().string("X-Robots-Tag", "noindex"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Content-Security-Policy", matchesPattern(
                            "default-src 'none'; style-src 'sha256-[A-Za-z0-9+/=]+'; base-uri 'none';"
                                    + " form-action 'none'; frame-ancestors 'none'")))
                    .andExpect(content().string(allOf(
                            containsString("name=\"viewport\""),
                            containsString("prefers-color-scheme:dark"),
                            not(containsString("<script")),
                            not(containsString("src=\"")),
                            not(containsString("href=")))));
        }
    }

    @Test
    void theStyleHashInThePolicyMatchesTheInlineStyle() throws Exception {
        var response = mockMvc.perform(get("/r/{code}", "doesNotExist")).andReturn().getResponse();
        String html = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String style = html.substring(html.indexOf("<style>") + 7, html.indexOf("</style>"));
        String hash = java.util.Base64.getEncoder().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(style.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(response.getHeader("Content-Security-Policy")).contains("'sha256-" + hash + "'");
    }

    private void expired(String code, String goneUrl, String goneMessage) {
        ShortLink link = new ShortLink(code, "https://example.com/secret-destination",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        link.setGoneOptions(goneUrl, goneMessage);
        repository.save(link);
    }
}

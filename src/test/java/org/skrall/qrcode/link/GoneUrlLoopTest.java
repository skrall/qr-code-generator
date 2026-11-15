package org.skrall.qrcode.link;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A gone link's fallback must not be one of this service's short links: once both have expired
 * they would redirect to each other, or a link to itself, forever. The public base URL in tests
 * is the default, {@code http://localhost:8080}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoneUrlLoopTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService service;

    @Autowired
    private ShortLinkRepository repository;

    @Autowired
    private LinkProperties properties;

    @Test
    void aLinkCannotFallBackToItselfOrAnotherShortLink() throws Exception {
        ShortLink other = service.create("https://example.com/other", null);

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/a","goneUrl":"%s"}""".formatted(properties.shortUrl(other.getCode()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString(
                        "goneUrl must not be one of this service's short links")));
    }

    @Test
    void editingCannotPointALinkAtItself() throws Exception {
        ShortLink link = service.create("https://example.com/self", null);

        mockMvc.perform(put("/api/links/{code}", link.getCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/self","goneUrl":"%s"}"""
                                .formatted("http://LOCALHOST:8080/r/" + link.getCode())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aFallbackSavedBeforeTheCheckAnswersGoneInsteadOfLooping() throws Exception {
        // Written straight to the repository, as a row saved before the check existed would be.
        ShortLink link = service.create("https://example.com/legacy", null);
        link.setGoneOptions(properties.shortUrl(link.getCode()), null);
        link.delete(Instant.now());
        repository.saveAndFlush(link);

        mockMvc.perform(get("/r/{code}", link.getCode()))
                .andExpect(status().isGone())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void anOrdinaryFallbackStillRedirects() throws Exception {
        ShortLink link = service.create("https://example.com/live", null);
        link.setGoneOptions("https://example.com/elsewhere", null);
        link.delete(Instant.now());
        repository.saveAndFlush(link);

        mockMvc.perform(get("/r/{code}", link.getCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/elsewhere"));
    }

    @Test
    void theSiteWideFallbackCannotBeAShortLink() {
        assertThatThrownBy(() -> new ShortLinkService(null, null, new LinkProperties("http://localhost:8080"),
                        new GoneProperties("http://localhost:8080/r/abc"), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.links.gone-url must not be one of this service's short links");
    }
}

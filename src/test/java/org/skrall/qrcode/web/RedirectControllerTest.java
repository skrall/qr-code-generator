package org.skrall.qrcode.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
}

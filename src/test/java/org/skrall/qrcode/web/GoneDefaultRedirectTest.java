package org.skrall.qrcode.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.skrall.qrcode.link.ShortLink;
import org.skrall.qrcode.link.ShortLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** {@code app.links.gone-url}: the fallback for gone links that have none of their own. */
@SpringBootTest(properties = "app.links.gone-url=https://example.com/default-gone")
@AutoConfigureMockMvc
class GoneDefaultRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService service;

    @Test
    void aGoneLinkWithoutItsOwnFallbackUsesTheDefault() throws Exception {
        ShortLink link = service.create("https://example.com/a", null);
        service.delete(link.getCode());

        mockMvc.perform(get("/r/{code}", link.getCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/default-gone"));
    }

    @Test
    void theLinksOwnFallbackWins() throws Exception {
        ShortLink link = service.create("https://example.com/b", null, "https://example.com/own", null);
        service.delete(link.getCode());

        mockMvc.perform(get("/r/{code}", link.getCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/own"));
    }

    @Test
    void aLinkWithItsOwnMessageShowsItRatherThanTheDefault() throws Exception {
        ShortLink link = service.create("https://example.com/c", null, null, "Thanks for coming.");
        service.delete(link.getCode());

        mockMvc.perform(get("/r/{code}", link.getCode()))
                .andExpect(status().isGone())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(containsString("Thanks for coming.")));
    }

    @Test
    void anUnknownCodeIsStillNotFound() throws Exception {
        mockMvc.perform(get("/r/{code}", "neverExisted")).andExpect(status().isNotFound());
    }
}

package org.skrall.qrcode.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skrall.qrcode.link.ShortLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class LinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkRepository repository;

    @BeforeEach
    void clearLinks() {
        repository.deleteAll();
    }

    @Test
    void createsALinkAndComposesTheShortUrl() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/page?q=1"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl").value(
                        matchesPattern("http://localhost:8080/r/[0-9A-Za-z]{9}")))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/page?q=1"))
                .andExpect(jsonPath("$.hitCount").value(0))
                .andReturn();

        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("http://localhost:8080/r/" + code);
    }

    @Test
    void listsNewestFirstAndSearchesTheDestination() throws Exception {
        create("https://alpha.example.com/one");
        create("https://beta.example.com/two");

        mockMvc.perform(get("/api/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].targetUrl").value("https://beta.example.com/two"));

        mockMvc.perform(get("/api/links").param("q", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].targetUrl").value("https://alpha.example.com/one"));
    }

    @Test
    void searchAlsoMatchesTheCode() throws Exception {
        String code = create("https://example.com");

        mockMvc.perform(get("/api/links").param("q", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].code").value(code));
    }

    @Test
    void deletesALinkAndReportsAMissingOne() throws Exception {
        String code = create("https://example.com");

        mockMvc.perform(delete("/api/links/{code}", code))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/links/{code}", code))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/links"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rejectsASchemeThatWouldRunInTheBrowser() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"javascript:alert(1)"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(containsString("http or https")));
    }

    @Test
    void rejectsCredentialsInTheDestination() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://user:secret@example.com"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("credentials")));
    }

    @Test
    void rejectsAnExpiryThatHasAlreadyPassed() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com","expiresAt":"2000-01-01T00:00:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("future")));
    }

    @Test
    void rejectsABlankDestinationAsAFieldProblem() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("url must not be blank"))
                .andExpect(jsonPath("$.errors[0].field").value("url"));
    }

    private String create(String url) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }
}

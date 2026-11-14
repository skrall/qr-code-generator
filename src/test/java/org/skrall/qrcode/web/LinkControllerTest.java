package org.skrall.qrcode.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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
    void editsADestinationWithoutChangingTheCode() throws Exception {
        String code = create("https://example.com/old");

        mockMvc.perform(put("/api/links/{code}", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/new"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/new"));

        mockMvc.perform(get("/r/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/new"));
    }

    @Test
    void anExpiredLinkCanBeRevivedByEditingItsExpiry() throws Exception {
        String code = create("https://example.com");
        var link = repository.findByCode(code).orElseThrow();
        link.update(link.getTargetUrl(), Instant.now().minusSeconds(60));
        repository.save(link);

        mockMvc.perform(get("/r/{code}", code))
                .andExpect(status().isGone());

        mockMvc.perform(put("/api/links/{code}", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com","expiresAt":"2999-01-01T00:00:00Z"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        mockMvc.perform(get("/r/{code}", code))
                .andExpect(status().isFound());
    }

    @Test
    void editingReportsAMissingOrDeletedLink() throws Exception {
        mockMvc.perform(put("/api/links/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com"}"""))
                .andExpect(status().isNotFound());

        String code = create("https://example.com");
        mockMvc.perform(delete("/api/links/{code}", code))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/links/{code}", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/new"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void editingRejectsANonHttpDestination() throws Exception {
        String code = create("https://example.com");

        mockMvc.perform(put("/api/links/{code}", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"javascript:alert(1)"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("http or https")));

        mockMvc.perform(get("/r/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
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

    @Test
    void storesAndReturnsTheGoneOptions() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com","goneUrl":" https://example.com/next ",
                                 "goneMessage":"  This offer has ended.  "}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goneUrl").value("https://example.com/next"))
                .andExpect(jsonPath("$.goneMessage").value("This offer has ended."))
                .andReturn();
        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");

        mockMvc.perform(get("/api/links"))
                .andExpect(jsonPath("$.items[0].goneUrl").value("https://example.com/next"))
                .andExpect(jsonPath("$.items[0].goneMessage").value("This offer has ended."));

        mockMvc.perform(put("/api/links/{code}", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com","goneMessage":"Moved on."}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.goneUrl").doesNotExist())
                .andExpect(jsonPath("$.goneMessage").value("Moved on."));

        mockMvc.perform(put("/api/links/{code}", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com","goneUrl":"","goneMessage":"  "}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goneUrl").doesNotExist())
                .andExpect(jsonPath("$.goneMessage").doesNotExist());
    }

    @Test
    void rejectsAFallbackThatIsNotHttp() throws Exception {
        for (String bad : new String[] {"javascript:alert(1)", "data:text/html,hi", "ftp://example.com"}) {
            mockMvc.perform(post("/api/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"https://example.com\",\"goneUrl\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("goneUrl must be an absolute http or https URL"));
        }
        String code = create("https://example.com/edit");
        mockMvc.perform(put("/api/links/{code}", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com","goneUrl":"javascript:alert(1)"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnOversizedFallbackOrMessage() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"goneUrl\":\"https://example.com/"
                                + "a".repeat(2100) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("goneUrl"));
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"goneMessage\":\""
                                + "a".repeat(281) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("goneMessage must be at most 280 characters"))
                .andExpect(jsonPath("$.errors[0].field").value("goneMessage"));
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"goneMessage\":\""
                                + "a".repeat(280) + "\"}"))
                .andExpect(status().isCreated());
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

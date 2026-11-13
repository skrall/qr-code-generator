package org.skrall.qrcode.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class QrCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersPngByDefault() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"https://example.com","style":{"size":256}}"""))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"qr-code.png\""))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).hasSizeGreaterThan(8);
        assertThat(body[0]).isEqualTo((byte) 0x89);
        assertThat(new String(body, 1, 3, StandardCharsets.US_ASCII)).isEqualTo("PNG");
    }

    @Test
    void rendersSvgWhenAsked() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/qr-codes?format=svg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"https://example.com","style":{"size":200}}"""))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andReturn();

        String svg = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(svg).contains("<svg").contains("width=\"200\"");
    }

    @Test
    void rejectsBlankContentWithAFieldProblem() throws Exception {
        mockMvc.perform(post("/api/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("data must not be blank"))
                .andExpect(jsonPath("$.errors[0].field").value("data"));
    }

    @Test
    void rendersAGradient() throws Exception {
        mockMvc.perform(post("/api/qr-codes?format=svg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"hello","style":{"gradient":{"type":"RADIAL","start":"#101820","end":"#3158c4"}}}"""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<radialGradient")));
    }

    @Test
    void rejectsAGradientThatWouldEraseTheCode() throws Exception {
        mockMvc.perform(post("/api/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"hello","style":{"gradient":{"start":"#101820","end":"#f3f4f6"}}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("style.gradient.end #f3f4f6 is too close")));
    }

    @Test
    void rejectsAGradientWithoutValidColours() throws Exception {
        mockMvc.perform(post("/api/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"hello","style":{"gradient":{"start":"red","angle":400}}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "style.gradient.start", "style.gradient.end", "style.gradient.angle")));
    }

    @Test
    void rejectsFrameTextTheFontCannotDraw() throws Exception {
        mockMvc.perform(post("/api/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"hello","style":{"frame":{"title":"扫一扫"}}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("which the bundled font cannot draw")));
    }

    @Test
    void rejectsAFrameWithOutOfRangeSizes() throws Exception {
        mockMvc.perform(post("/api/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"hello","style":{"frame":{"titleSizePercent":50,"bleedPercent":-1,"textColor":"red"}}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "style.frame.titleSizePercent", "style.frame.bleedPercent", "style.frame.textColor")));
    }

    @Test
    void rejectsAnUnusableLogo() throws Exception {
        mockMvc.perform(post("/api/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"hello","style":{"logo":{"dataUrl":"not-a-data-url"}}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("data URL")));
    }

    @Test
    void rejectsAnUnusableBackgroundImage() throws Exception {
        mockMvc.perform(post("/api/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"hello","style":{"backgroundImage":{"dataUrl":"not-a-data-url"}}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("data URL")));
    }

    @Test
    void rejectsContentThatDoesNotFit() throws Exception {
        mockMvc.perform(post("/api/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"%s"}""".formatted("a".repeat(4000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("too long")));
    }

    @Test
    void rejectsAnUnknownFormat() throws Exception {
        mockMvc.perform(post("/api/qr-codes?format=gif")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"hello"}"""))
                .andExpect(status().isBadRequest());
    }
}

package org.skrall.qrcode.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The design API with sign-in off, where every request is the same owner. Visibility and who may
 * change what are covered by {@code DesignSecurityTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DesignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void savesAndLoadsADesignWithItsLogo() throws Exception {
        String logo = redPng();
        long id = create("""
                {"name":"  Brand  ","style":{"size":512,"moduleShape":"DOTS","finderColor":"#3158c4",
                 "errorCorrection":"HIGH","logo":{"dataUrl":"%s","sizePercent":20}}}""".formatted(logo));

        mockMvc.perform(get("/api/designs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Brand"))
                .andExpect(jsonPath("$.shared").value(false))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.style.moduleShape").value("DOTS"))
                .andExpect(jsonPath("$.style.finderColor").value("#3158c4"))
                .andExpect(jsonPath("$.style.logo.dataUrl").value(logo));
    }

    @Test
    void theListLeavesOutTheStyle() throws Exception {
        long id = create("""
                {"name":"Listed","style":{"size":256}}""");

        mockMvc.perform(get("/api/designs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].name".formatted(id)).value(hasItem("Listed")))
                .andExpect(jsonPath("$[0].style").doesNotExist());
    }

    @Test
    void renamingAndSharingKeepTheStyleWhenNoneIsSent() throws Exception {
        long id = create("""
                {"name":"Before","style":{"moduleShape":"STAR"}}""");

        mockMvc.perform(put("/api/designs/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"After","shared":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.shared").value(true))
                .andExpect(jsonPath("$.style.moduleShape").value("STAR"));
    }

    @Test
    void overwritesTheStyleWhenOneIsSent() throws Exception {
        long id = create("""
                {"name":"Overwrite","shared":true,"style":{"moduleShape":"STAR"}}""");

        mockMvc.perform(put("/api/designs/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Overwrite","style":{"moduleShape":"LIQUID"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style.moduleShape").value("LIQUID"))
                // Absent shared keeps the current setting rather than unsharing.
                .andExpect(jsonPath("$.shared").value(true));
    }

    @Test
    void deletes() throws Exception {
        long id = create("""
                {"name":"Doomed","style":{}}""");

        mockMvc.perform(delete("/api/designs/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/designs/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/designs"))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));
    }

    @Test
    void rejectsABlankNameAndAMissingStyleAsFieldProblems() throws Exception {
        mockMvc.perform(post("/api/designs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("style")));
    }

    @Test
    void refusesAStyleThatCouldNotRender() throws Exception {
        // A gradient that fades into the background is rejected by the renderer, so it is
        // rejected when saved too, not when someone later loads the design.
        mockMvc.perform(post("/api/designs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Faint","style":{"background":"#ffffff",
                                 "gradient":{"type":"LINEAR","start":"#fefefe","end":"#fdfdfd","angle":0}}}"""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/designs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Broken logo","style":{"logo":{"dataUrl":"data:image/png;base64,bm90IGFuIGltYWdl"}}}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownDesignIsNotFound() throws Exception {
        mockMvc.perform(get("/api/designs/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No design 999999 is visible to you."));
    }

    private long create(String body) throws Exception {
        String response = mockMvc.perform(post("/api/designs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private static String redPng() throws IOException {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0xcc, 0x22, 0x22));
            graphics.fillRect(0, 0, 40, 40);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}

package org.skrall.qrcode.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void streamsAZipWithTheImagesAndTheReport() throws Exception {
        MvcResult started = mockMvc.perform(batch("data,filename\nhttps://example.com,site\n,blank\n",
                        """
                        {"format":"both","style":{"size":200}}"""))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"qr-codes.zip\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        Map<String, byte[]> zip = entries(result.getResponse().getContentAsByteArray());
        assertThat(zip.keySet()).containsExactly("site.png", "site.svg", "report.csv");
        assertThat(width(zip.get("site.png"))).isEqualTo(200);
        assertThat(new String(zip.get("report.csv"), StandardCharsets.UTF_8))
                .contains("2,blank,skipped,data must not be blank");
    }

    @Test
    void theOptionsPartIsOptional() throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/qr-codes/batch")
                .file(csv("data\nhello\n"));
        MvcResult started = mockMvc.perform(request).andExpect(request().asyncStarted()).andReturn();

        byte[] body = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(entries(body).keySet()).containsExactly("row-1.png", "report.csv");
    }

    @Test
    void takesTheStyleFromASavedDesign() throws Exception {
        long id = createDesign();

        MvcResult started = mockMvc.perform(batch("data\nhello\n", """
                        {"format":"png","designId":%d}""".formatted(id)))
                .andExpect(request().asyncStarted())
                .andReturn();
        byte[] body = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(width(entries(body).get("row-1.png"))).isEqualTo(160);
    }

    @Test
    void anUnknownDesignIsNotFound() throws Exception {
        mockMvc.perform(batch("data\nhello\n", """
                        {"designId":987654}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No design 987654 is visible to you."));
    }

    @Test
    void aCsvWithoutADataColumnIsABadRequest() throws Exception {
        mockMvc.perform(batch("url\nhttps://example.com\n", null))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The CSV's header row has no data column. Found: url."));
    }

    @Test
    void tooManyRowsIsABadRequest() throws Exception {
        mockMvc.perform(batch("data\n" + "x\n".repeat(501), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The CSV has more than 500 rows. Split it into smaller batches."));
    }

    @Test
    void anOversizedCsvIsABadRequest() throws Exception {
        mockMvc.perform(batch("data\n" + "x".repeat(1024 * 1024) + "\n", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The CSV must be at most 1 MB."));
    }

    @Test
    void anInvalidStyleFailsBeforeAnythingIsStreamed() throws Exception {
        mockMvc.perform(batch("data\nhello\n", """
                        {"style":{"size":20}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("style.size"))
                .andExpect(jsonPath("$.detail").value("style.size must be at least 128"));

        mockMvc.perform(batch("data\nhello\n", """
                        {"style":{"gradient":{"type":"LINEAR","start":"#ffffff","end":"#fefefe"}}}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownFormatIsABadRequest() throws Exception {
        mockMvc.perform(batch("data\nhello\n", """
                        {"format":"gif"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("format must be png, svg or both"));
    }

    @Test
    void aStyleAndADesignTogetherIsABadRequest() throws Exception {
        mockMvc.perform(batch("data\nhello\n", """
                        {"style":{},"designId":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Send a style or a designId, not both."));
    }

    @Test
    void aMissingCsvIsABadRequest() throws Exception {
        mockMvc.perform(multipart("/api/qr-codes/batch")
                        .file(new MockMultipartFile("options", "", "application/json", "{}".getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMalformedCsvIsABadRequest() throws Exception {
        mockMvc.perform(batch("data\n\"open\n", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Line 2 of the CSV opens a quoted field that is never closed."));
    }

    private static MockMultipartHttpServletRequestBuilder batch(String csv, String options) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/qr-codes/batch").file(csv(csv));
        if (options != null) {
            request.file(new MockMultipartFile("options", "", "application/json",
                    options.getBytes(StandardCharsets.UTF_8)));
        }
        return request;
    }

    private static MockMultipartFile csv(String text) {
        return new MockMultipartFile("csv", "codes.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }

    private long createDesign() throws Exception {
        String response = mockMvc.perform(post("/api/designs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Batch small","shared":false,"style":{"size":160}}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    static Map<String, byte[]> entries(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                in.transferTo(bytes);
                entries.put(entry.getName(), bytes.toByteArray());
            }
        }
        return entries;
    }

    private static int width(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        return image.getWidth();
    }
}

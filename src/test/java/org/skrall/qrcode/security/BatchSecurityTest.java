package org.skrall.qrcode.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Batch rendering under Basic: like a single render it is for anyone signed in, viewers
 * included, and a saved design is used only when the caller could load it.
 */
@SpringBootTest(properties = {"app.security.mode=basic", "app.security.viewer-users=alice,bob"})
@AutoConfigureMockMvc
class BatchSecurityTest {

    private static final RequestPostProcessor ALICE = httpBasic("alice", "test-password");
    private static final RequestPostProcessor BOB = httpBasic("bob", "test-password");
    private static final RequestPostProcessor OPERATOR = httpBasic("operator", "test-password");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAnonymousBatchIsRefused() throws Exception {
        mockMvc.perform(batch(null)).andExpect(status().isUnauthorized());
    }

    @Test
    void aViewerMayRenderABatch() throws Exception {
        MvcResult started = mockMvc.perform(batch(null).with(ALICE))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));
    }

    @Test
    void aViewerMayUseTheirOwnAndSharedDesigns() throws Exception {
        long own = createDesign(ALICE, false);
        long shared = createDesign(BOB, true);

        for (long id : new long[] {own, shared}) {
            MvcResult started = mockMvc.perform(batch(id).with(ALICE))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        }
    }

    @Test
    void someoneElsesPrivateDesignIsNotFoundOperatorsIncluded() throws Exception {
        long id = createDesign(ALICE, false);

        mockMvc.perform(batch(id).with(BOB)).andExpect(status().isNotFound());
        mockMvc.perform(batch(id).with(OPERATOR)).andExpect(status().isNotFound());
    }

    private static MockMultipartHttpServletRequestBuilder batch(Long designId) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/qr-codes/batch")
                .file(new MockMultipartFile("csv", "codes.csv", "text/csv",
                        "data\nhello\n".getBytes(StandardCharsets.UTF_8)));
        if (designId != null) {
            request.file(new MockMultipartFile("options", "", "application/json",
                    ("{\"designId\":" + designId + "}").getBytes(StandardCharsets.UTF_8)));
        }
        return request;
    }

    private long createDesign(RequestPostProcessor user, boolean shared) throws Exception {
        String response = mockMvc.perform(post("/api/designs").with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Batch design","shared":%s,"style":{"size":160}}""".formatted(shared)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}

package org.skrall.qrcode.security;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Who sees and who may change a design, with real Basic users: alice and bob are viewers, and
 * operator is the OPERATOR. A private design is invisible to everyone but its owner, operators
 * included; a shared one is visible to all and changeable by its owner and by operators.
 */
@SpringBootTest(properties = {"app.security.mode=basic", "app.security.viewer-users=alice,bob"})
@AutoConfigureMockMvc
class DesignSecurityTest {

    private static final RequestPostProcessor ALICE = httpBasic("alice", "test-password");
    private static final RequestPostProcessor BOB = httpBasic("bob", "test-password");
    private static final RequestPostProcessor OPERATOR = httpBasic("operator", "test-password");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anyoneSignedInMaySaveADesign() throws Exception {
        long id = create(ALICE, "Viewer's own", false);

        mockMvc.perform(get("/api/designs/{id}", id).with(ALICE))
                .andExpect(jsonPath("$.owner").value("alice"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void aPrivateDesignIsInvisibleToEveryoneElseOperatorsIncluded() throws Exception {
        long id = create(ALICE, "Alice private", false);

        for (RequestPostProcessor other : new RequestPostProcessor[] {BOB, OPERATOR}) {
            mockMvc.perform(get("/api/designs").with(other))
                    .andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));
            mockMvc.perform(get("/api/designs/{id}", id).with(other)).andExpect(status().isNotFound());
            mockMvc.perform(put("/api/designs/{id}", id).with(other)
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"name":"Taken"}"""))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/designs/{id}", id).with(other)).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/designs/{id}", id).with(ALICE)).andExpect(status().isOk());
    }

    @Test
    void aSharedDesignIsVisibleToAllButOnlyItsOwnerAndOperatorsMayChangeIt() throws Exception {
        long id = create(ALICE, "Alice shared", true);

        mockMvc.perform(get("/api/designs/{id}", id).with(BOB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.editable").value(false));
        mockMvc.perform(put("/api/designs/{id}", id).with(BOB)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Bob was here"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "Only the design's owner, or an operator for a shared design, can change it."));
        mockMvc.perform(delete("/api/designs/{id}", id).with(BOB)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/designs/{id}", id).with(OPERATOR))
                .andExpect(jsonPath("$.editable").value(true));
        mockMvc.perform(put("/api/designs/{id}", id).with(OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Tidied by operator"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tidied by operator"))
                // Ownership does not move to whoever edits it.
                .andExpect(jsonPath("$.owner").value("alice"));
        mockMvc.perform(delete("/api/designs/{id}", id).with(OPERATOR)).andExpect(status().isNoContent());
    }

    @Test
    void unsharingHidesTheDesignAgain() throws Exception {
        long id = create(ALICE, "Briefly shared", true);
        mockMvc.perform(get("/api/designs/{id}", id).with(BOB)).andExpect(status().isOk());

        mockMvc.perform(put("/api/designs/{id}", id).with(ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Briefly shared","shared":false}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/designs/{id}", id).with(BOB)).andExpect(status().isNotFound());
    }

    @Test
    void anAnonymousCallIsRefused() throws Exception {
        mockMvc.perform(get("/api/designs")).andExpect(status().isUnauthorized());
    }

    private long create(RequestPostProcessor user, String name, boolean shared) throws Exception {
        String response = mockMvc.perform(post("/api/designs").with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","shared":%s,"style":{"moduleShape":"ROUNDED"}}"""
                                .formatted(name, shared)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}

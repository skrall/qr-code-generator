package org.skrall.qrcode.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.skrall.qrcode.link.ScanService;
import org.skrall.qrcode.link.ShortLink;
import org.skrall.qrcode.link.ShortLinkRepository;
import org.skrall.qrcode.link.ShortLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A scan that cannot be recorded never costs the scanner the redirect, or the hit count. */
@SpringBootTest
@AutoConfigureMockMvc
class ScanFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService service;

    @Autowired
    private ShortLinkRepository links;

    @MockitoBean
    private ScanService scans;

    @Test
    void theRedirectSurvivesAFailedScanRecord() throws Exception {
        doThrow(new DataAccessResourceFailureException("link_scan is unavailable"))
                .when(scans).record(any(), any(), any());
        ShortLink link = service.create("https://example.com/still-works", null);

        mockMvc.perform(get("/r/{code}", link.getCode()).header("User-Agent", "Mozilla/5.0 (iPhone)"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/still-works"));

        verify(scans).record(any(), any(), any());
        assertThat(links.findByCode(link.getCode()).orElseThrow().getHitCount()).isEqualTo(1);
    }
}

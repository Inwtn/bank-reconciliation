package com.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsPublicButFinancialDataRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/actuator/health").contextPath("/api"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lancamentos").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanReadButCannotCreateEntriesOrReadMetrics() throws Exception {
        mvc.perform(get("/api/lancamentos").contextPath("/api")
                        .with(httpBasic("viewer", "test-viewer-password")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/lancamentos").contextPath("/api")
                        .with(httpBasic("viewer", "test-viewer-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/actuator/prometheus").contextPath("/api")
                        .with(httpBasic("viewer", "test-viewer-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadPrometheusMetrics() throws Exception {
        mvc.perform(get("/api/actuator/prometheus").contextPath("/api")
                        .with(httpBasic("admin", "test-admin-password")))
                .andExpect(status().isOk());
    }
}

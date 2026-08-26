package com.cardbilling.billing.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.cardbilling.billing.infrastructure.BillingPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The secured configuration - the one that applies when no profile says otherwise, which is what
 * production runs.
 *
 * <p>Deliberately does not activate the {@code local} profile, so this exercises the real
 * resource-server chain rather than the relaxed one the other API tests use. A JWKS URI is
 * pointed at a port nothing listens on: unlike {@code issuer-uri}, it is fetched lazily, so the
 * context starts without Keycloak and every request still has to present a token to get past the
 * filter. That is precisely what is being asserted here - that the default is closed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "billing.seed-demo-data=false",
        // These tests never get past the security filter, so they need no schema of their own -
        // and leaving it alone keeps this context from dropping the one the other tests share.
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:59999/realms/card-billing/protocol/openid-connect/certs"
})
class SecurityIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BillingPostgres.registerDatasource(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("reading overdue invoices without a token is refused")
    void overdueRequiresAToken() throws Exception {
        mockMvc.perform(get("/invoices/overdue").param("asOf", "2026-03-26"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("searching without a token is refused")
    void searchRequiresAToken() throws Exception {
        mockMvc.perform(get("/invoices/search")
                        .param("documentNumber", "10000000042")
                        .param("amountCents", "100000")
                        .param("aroundDate", "2026-03-25"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("applying interest without a token is refused")
    void applyingInterestRequiresAToken() throws Exception {
        mockMvc.perform(post("/invoices/{id}/interest", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accrualDate": "2026-03-26"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("recording a payment without a token is refused")
    void recordingAPaymentRequiresAToken() throws Exception {
        mockMvc.perform(post("/invoices/{id}/payments", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 1000, "source": "INTERNAL", "paidAt": "2026-03-24T10:00:00"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("closing a cycle without a token is refused")
    void closingACycleRequiresAToken() throws Exception {
        mockMvc.perform(post("/cycles/close").param("date", "2026-03-15"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a garbage bearer token is refused rather than ignored")
    void garbageTokenIsRefused() throws Exception {
        mockMvc.perform(get("/invoices/overdue")
                        .param("asOf", "2026-03-26")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the health probe stays open, so a container orchestrator can reach it")
    void healthProbeIsOpen() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}

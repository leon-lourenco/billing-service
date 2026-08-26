package com.cardbilling.billing.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.BillingCycle;
import com.cardbilling.billing.domain.Cardholder;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.infrastructure.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The API end to end: real controllers, real use cases, real Postgres. Runs under the
 * {@code local} profile, so these assert routing, status codes and behaviour without a token in
 * the way - {@link SecurityIntegrationTest} covers the default, secured configuration separately.
 */
class InvoiceApiIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate CLOSING_DATE = LocalDate.of(2026, 3, 15);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 25);
    private static final AtomicLong UNIQUE = new AtomicLong(100);
    private static final AtomicLong CUSTOMER_ID = new AtomicLong(900);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InvoiceRepositoryPort invoices;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        }
        return mockMvc;
    }

    private DocumentNumber uniqueCardholder() {
        return DocumentNumber.of(String.format("%011d", 40_000_000_000L + UNIQUE.incrementAndGet()));
    }

    private Invoice anInvoiceFor(DocumentNumber cardholder, long totalCents) {
        return invoices.save(Invoice.close(1L, Cardholder.of(CUSTOMER_ID.incrementAndGet(), cardholder),
                BillingCycle.closingOn(CLOSING_DATE), Money.ofCents(totalCents)));
    }

    @Nested
    @DisplayName("GET /invoices/overdue")
    class Overdue {

        @Test
        @DisplayName("returns an unpaid invoice once its due date has passed")
        void returnsOverdueInvoices() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 150_000);

            mvc().perform(get("/invoices/overdue").param("asOf", DUE_DATE.plusDays(1).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == %d)]".formatted(invoice.id()), hasSize(1)))
                    .andExpect(jsonPath("$[?(@.id == %d)].amountOwedCents".formatted(invoice.id()))
                            .value(150_000));
        }

        @Test
        @DisplayName("carries the numeric customer id, which collections-service passes on when notifying")
        void exposesCustomerId() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 160_000);

            mvc().perform(get("/invoices/overdue").param("asOf", DUE_DATE.plusDays(1).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == %d)].customerId".formatted(invoice.id()))
                            .value(Math.toIntExact(invoice.customerId())))
                    .andExpect(jsonPath("$[?(@.id == %d)].documentNumber".formatted(invoice.id()))
                            .value(invoice.customerDocumentNumber().value()));
        }

        @Test
        @DisplayName("rejects a missing asOf rather than guessing today")
        void requiresAsOf() throws Exception {
            mvc().perform(get("/invoices/overdue"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /invoices/search")
    class Search {

        @Test
        @DisplayName("finds the invoice a statement line describes")
        void findsMatchingInvoice() throws Exception {
            DocumentNumber cardholder = uniqueCardholder();
            Invoice invoice = anInvoiceFor(cardholder, 250_000);

            mvc().perform(get("/invoices/search")
                            .param("documentNumber", cardholder.value())
                            .param("amountCents", "250000")
                            .param("aroundDate", DUE_DATE.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(invoice.id()))
                    .andExpect(jsonPath("$[0].documentNumber").value(cardholder.value()))
                    // reconciliation-service matches on the document, but still gets the id back
                    .andExpect(jsonPath("$[0].customerId").value(Math.toIntExact(invoice.customerId())));
        }

        @Test
        @DisplayName("defaults the tolerance to three days")
        void defaultsToleranceToThreeDays() throws Exception {
            DocumentNumber cardholder = uniqueCardholder();
            anInvoiceFor(cardholder, 260_000);

            mvc().perform(get("/invoices/search")
                            .param("documentNumber", cardholder.value())
                            .param("amountCents", "260000")
                            .param("aroundDate", DUE_DATE.plusDays(3).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));

            mvc().perform(get("/invoices/search")
                            .param("documentNumber", cardholder.value())
                            .param("amountCents", "260000")
                            .param("aroundDate", DUE_DATE.plusDays(4).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("answers a malformed document number with a problem detail, not a 500")
        void rejectsMalformedDocumentNumber() throws Exception {
            mvc().perform(get("/invoices/search")
                            .param("documentNumber", "not-a-document")
                            .param("amountCents", "1000")
                            .param("aroundDate", DUE_DATE.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.detail", containsString("11 digits")));
        }
    }

    @Nested
    @DisplayName("POST /invoices/{id}/interest")
    class ApplyInterest {

        @Test
        @DisplayName("charges the policy amount when the caller supplies none")
        void appliesPolicyAmounts() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 100_000);

            mvc().perform(post("/invoices/{id}/interest", invoice.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"accrualDate": "2026-03-26"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.applied").value(true))
                    .andExpect(jsonPath("$.invoice.interestAppliedCents").value(3_000))
                    .andExpect(jsonPath("$.invoice.status").value("OVERDUE"));
        }

        @Test
        @DisplayName("applies the caller's own amounts when it supplies them")
        void appliesCallerAmounts() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 100_000);

            mvc().perform(post("/invoices/{id}/interest", invoice.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"feeCents": 1500, "dailyInterestCents": 750, "accrualDate": "2026-03-26"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.invoice.interestAppliedCents").value(2_250));
        }

        @Test
        @DisplayName("a replay for the same day changes nothing and reports applied=false")
        void replayIsANoOp() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 100_000);
            String body = """
                    {"accrualDate": "2026-03-26"}
                    """;
            mvc().perform(post("/invoices/{id}/interest", invoice.id())
                    .contentType(MediaType.APPLICATION_JSON).content(body));

            mvc().perform(post("/invoices/{id}/interest", invoice.id())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.applied").value(false))
                    .andExpect(jsonPath("$.invoice.interestAppliedCents").value(3_000));
        }

        @Test
        @DisplayName("an unknown invoice is a 404 problem detail")
        void unknownInvoiceIsNotFound() throws Exception {
            mvc().perform(post("/invoices/{id}/interest", 999_999)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"accrualDate": "2026-03-26"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Invoice not found"))
                    .andExpect(jsonPath("$.invoiceId").value(999_999));
        }

        @Test
        @DisplayName("a missing accrual date is a 400 naming the field")
        void missingAccrualDateIsRejected() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 100_000);

            mvc().perform(post("/invoices/{id}/interest", invoice.id())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.accrualDate").value("accrualDate is required"));
        }
    }

    @Nested
    @DisplayName("POST /invoices/{id}/payments")
    class RecordPayment {

        @Test
        @DisplayName("records a payment and marks the invoice paid once covered")
        void recordsPaymentAndMarksPaid() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 100_000);

            mvc().perform(post("/invoices/{id}/payments", invoice.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amountCents": 100000, "source": "EXTERNAL_RECONCILIATION",
                                     "externalReference": "STMT-API-1", "paidAt": "2026-03-24T10:00:00"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.recorded").value(true))
                    .andExpect(jsonPath("$.invoice.status").value("PAID"))
                    .andExpect(jsonPath("$.invoice.amountDueCents").value(0));
        }

        @Test
        @DisplayName("a partial payment leaves the invoice open")
        void partialPaymentLeavesInvoiceOpen() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 100_000);

            mvc().perform(post("/invoices/{id}/payments", invoice.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amountCents": 30000, "source": "EXTERNAL_RECONCILIATION",
                                     "externalReference": "STMT-API-PARTIAL", "paidAt": "2026-03-24T10:00:00"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.invoice.status").value("CLOSED"))
                    .andExpect(jsonPath("$.invoice.amountDueCents").value(70_000));
        }

        @Test
        @DisplayName("replaying the same statement line answers 200 without recording a second payment")
        void replayedMatchIsANoOp() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 100_000);
            String body = """
                    {"amountCents": 100000, "source": "EXTERNAL_RECONCILIATION",
                     "externalReference": "STMT-API-REPLAY", "paidAt": "2026-03-24T10:00:00"}
                    """;
            mvc().perform(post("/invoices/{id}/payments", invoice.id())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());

            mvc().perform(post("/invoices/{id}/payments", invoice.id())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recorded").value(false));
        }

        @Test
        @DisplayName("a non-positive amount is rejected with the field named")
        void rejectsNonPositiveAmount() throws Exception {
            Invoice invoice = anInvoiceFor(uniqueCardholder(), 100_000);

            mvc().perform(post("/invoices/{id}/payments", invoice.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amountCents": 0, "source": "INTERNAL", "paidAt": "2026-03-24T10:00:00"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amountCents").value("amountCents must be positive"));
        }
    }

    @Nested
    @DisplayName("POST /cycles/close")
    class CloseCycle {

        @Test
        @DisplayName("reports what the run did, even when there is nothing to close")
        void reportsClosingSummary() throws Exception {
            mvc().perform(post("/cycles/close").param("date", "2026-03-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.closingDate").value("2026-03-15"))
                    .andExpect(jsonPath("$.invoicesClosed").exists())
                    .andExpect(jsonPath("$.cardsConsidered").exists());
        }
    }
}

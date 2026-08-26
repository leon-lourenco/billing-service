package com.cardbilling.billing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.billing.application.InvoiceSearchQuery;
import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.BillingCycle;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.InterestCharge;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.domain.Payment;
import com.cardbilling.billing.infrastructure.PostgresIntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The mapping and the queries, against a real Postgres. The use case tests already cover the
 * rules; what these prove is that the SQL underneath agrees with the in-memory fakes those tests
 * ran against.
 */
class InvoicePersistenceIntegrationTest extends PostgresIntegrationTest {

    private static final DocumentNumber CARDHOLDER = DocumentNumber.of("10000000042");
    private static final LocalDate CLOSING_DATE = LocalDate.of(2026, 3, 15);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 25);

    @Autowired
    private InvoiceRepositoryPort invoices;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Invoice newInvoice(DocumentNumber cardholder, long totalCents) {
        return invoices.save(Invoice.close(1L, cardholder, BillingCycle.closingOn(CLOSING_DATE),
                Money.ofCents(totalCents)));
    }

    private InvoiceSearchQuery query(DocumentNumber cardholder, long amountCents, LocalDate aroundDate) {
        return new InvoiceSearchQuery(cardholder, Money.ofCents(amountCents), aroundDate,
                InvoiceSearchQuery.DEFAULT_TOLERANCE_DAYS);
    }

    @Test
    @DisplayName("a saved invoice comes back with every field, payment and accrual intact")
    void roundTripsTheWholeAggregate() {
        Invoice invoice = newInvoice(CARDHOLDER, 100_000);
        invoice.accrueInterest(InterestCharge.of(Money.ofCents(2_000), Money.ofCents(1_000)), DUE_DATE.plusDays(1));
        invoice.recordPayment(Money.ofCents(40_000), LocalDateTime.of(2026, 3, 27, 10, 0),
                Payment.Source.EXTERNAL_RECONCILIATION, "STMT-ROUNDTRIP");
        invoices.save(invoice);

        Invoice reloaded = invoices.findById(invoice.id()).orElseThrow();

        assertThat(reloaded.customerDocumentNumber()).isEqualTo(CARDHOLDER);
        assertThat(reloaded.referenceMonth()).isEqualTo("2026-03");
        assertThat(reloaded.closingDate()).isEqualTo(CLOSING_DATE);
        assertThat(reloaded.dueDate()).isEqualTo(DUE_DATE);
        assertThat(reloaded.totalAmount()).isEqualTo(Money.ofCents(100_000));
        assertThat(reloaded.interestApplied()).isEqualTo(Money.ofCents(3_000));
        assertThat(reloaded.amountOwed()).isEqualTo(Money.ofCents(103_000));
        assertThat(reloaded.amountPaid()).isEqualTo(Money.ofCents(40_000));
        assertThat(reloaded.amountDue()).isEqualTo(Money.ofCents(63_000));
        assertThat(reloaded.lastInterestAccrualDate()).contains(DUE_DATE.plusDays(1));
        assertThat(reloaded.status()).isEqualTo(Invoice.Status.OVERDUE);
        assertThat(reloaded.payments()).hasSize(1);
        assertThat(reloaded.interestAccruals()).hasSize(1);
        assertThat(reloaded.interestAccruals().getFirst().charge().lateFee()).isEqualTo(Money.ofCents(2_000));
    }

    @Test
    @DisplayName("saving twice appends rather than duplicating what is already stored")
    void savingTwiceDoesNotDuplicateChildRows() {
        Invoice invoice = newInvoice(CARDHOLDER, 100_000);
        invoice.accrueInterest(InterestCharge.of(Money.ofCents(2_000), Money.ofCents(1_000)), DUE_DATE.plusDays(1));
        invoices.save(invoice);

        Invoice reloaded = invoices.findById(invoice.id()).orElseThrow();
        reloaded.accrueInterest(InterestCharge.of(Money.ZERO, Money.ofCents(1_000)), DUE_DATE.plusDays(2));
        invoices.save(reloaded);

        Invoice finalState = invoices.findById(invoice.id()).orElseThrow();
        assertThat(finalState.interestAccruals()).hasSize(2);
        assertThat(finalState.interestApplied()).isEqualTo(Money.ofCents(4_000));
    }

    @Test
    @DisplayName("the overdue query returns unpaid invoices past due and nothing else")
    void findsOverdueInvoices() {
        Invoice unpaid = newInvoice(DocumentNumber.of("10000000050"), 100_000);
        Invoice paid = newInvoice(DocumentNumber.of("10000000051"), 100_000);
        paid.recordPayment(Money.ofCents(100_000), LocalDateTime.of(2026, 3, 24, 10, 0),
                Payment.Source.INTERNAL, null);
        invoices.save(paid);

        List<Invoice> overdue = invoices.findOverdueAsOf(DUE_DATE.plusDays(1));

        assertThat(overdue).extracting(Invoice::id).contains(unpaid.id()).doesNotContain(paid.id());
        assertThat(invoices.findOverdueAsOf(DUE_DATE)).extracting(Invoice::id).doesNotContain(unpaid.id());
    }

    @Test
    @DisplayName("search matches on document, amount owed and a due-date window")
    void searchMatchesOnAllThreeCriteria() {
        DocumentNumber cardholder = DocumentNumber.of("10000000060");
        Invoice match = newInvoice(cardholder, 100_000);
        newInvoice(DocumentNumber.of("10000000061"), 100_000);

        assertThat(invoices.search(query(cardholder, 100_000, DUE_DATE)))
                .extracting(Invoice::id).containsExactly(match.id());
        assertThat(invoices.search(query(cardholder, 100_000, DUE_DATE.plusDays(3)))).hasSize(1);
        assertThat(invoices.search(query(cardholder, 100_000, DUE_DATE.plusDays(4)))).isEmpty();
        assertThat(invoices.search(query(cardholder, 99_999, DUE_DATE))).isEmpty();
    }

    @Test
    @DisplayName("search matches the amount owed after interest, not the closed total")
    void searchMatchesAmountOwedIncludingInterest() {
        DocumentNumber cardholder = DocumentNumber.of("10000000062");
        Invoice invoice = newInvoice(cardholder, 200_000);
        invoice.accrueInterest(InterestCharge.of(Money.ofCents(4_000), Money.ofCents(2_000)), DUE_DATE.plusDays(1));
        invoices.save(invoice);

        assertThat(invoices.search(query(cardholder, 200_000, DUE_DATE))).isEmpty();
        assertThat(invoices.search(query(cardholder, 206_000, DUE_DATE)))
                .extracting(Invoice::id).containsExactly(invoice.id());
    }

    @Test
    @DisplayName("search never returns a paid invoice")
    void searchExcludesPaidInvoices() {
        DocumentNumber cardholder = DocumentNumber.of("10000000063");
        Invoice invoice = newInvoice(cardholder, 300_000);
        invoice.recordPayment(Money.ofCents(300_000), LocalDateTime.of(2026, 3, 24, 10, 0),
                Payment.Source.INTERNAL, null);
        invoices.save(invoice);

        assertThat(invoices.search(query(cardholder, 300_000, DUE_DATE))).isEmpty();
    }

    @Test
    @DisplayName("a statement line already paid is reported as such across every invoice")
    void detectsExistingExternalReference() {
        Invoice invoice = newInvoice(DocumentNumber.of("10000000064"), 400_000);
        invoice.recordPayment(Money.ofCents(400_000), LocalDateTime.of(2026, 3, 24, 10, 0),
                Payment.Source.EXTERNAL_RECONCILIATION, "STMT-EXISTING");
        invoices.save(invoice);

        assertThat(invoices.existsPaymentWithExternalReference("STMT-EXISTING")).isTrue();
        assertThat(invoices.existsPaymentWithExternalReference("STMT-NEVER-SEEN")).isFalse();
    }

    @Test
    @DisplayName("the indexes the search and overdue endpoints rely on actually exist")
    void indexesExist() {
        List<String> indexNames = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'invoices'", String.class);

        assertThat(indexNames).contains("idx_invoices_search", "idx_invoices_overdue");
    }

    @Test
    @DisplayName("the unique constraints backing both idempotency guarantees are in place")
    void uniqueConstraintsExist() {
        List<String> paymentIndexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'payments'", String.class);
        List<String> accrualIndexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'interest_accruals'", String.class);

        assertThat(paymentIndexes).contains("uk_payments_external_reference");
        assertThat(accrualIndexes).contains("uk_interest_accruals_invoice_date");
    }
}

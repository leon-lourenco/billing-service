package com.cardbilling.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.billing.domain.BillingCycle;
import com.cardbilling.billing.domain.Cardholder;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.InterestCharge;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.domain.Payment;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchInvoicesUseCaseTest {

    private static final DocumentNumber CARDHOLDER = DocumentNumber.of("10000000042");
    private static final DocumentNumber SOMEONE_ELSE = DocumentNumber.of("10000000099");
    /** Closing on the 15th puts the due date on the 25th. */
    private static final LocalDate CLOSING_DATE = LocalDate.of(2026, 3, 15);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 25);

    private InMemoryInvoiceRepository invoices;
    private SearchInvoicesUseCase useCase;

    @BeforeEach
    void setUp() {
        invoices = new InMemoryInvoiceRepository();
        useCase = new SearchInvoicesUseCase(invoices);
    }

    private Invoice invoiceFor(DocumentNumber cardholder, long totalCents) {
        return invoices.save(Invoice.close(1L, Cardholder.of(7L, cardholder), BillingCycle.closingOn(CLOSING_DATE),
                Money.ofCents(totalCents)));
    }

    private InvoiceSearchQuery query(DocumentNumber cardholder, long amountCents, LocalDate aroundDate) {
        return new InvoiceSearchQuery(cardholder, Money.ofCents(amountCents), aroundDate,
                InvoiceSearchQuery.DEFAULT_TOLERANCE_DAYS);
    }

    @Test
    @DisplayName("finds the invoice matching cardholder, amount owed and due date")
    void findsMatchingInvoice() {
        Invoice expected = invoiceFor(CARDHOLDER, 100_000);

        assertThat(useCase.search(query(CARDHOLDER, 100_000, DUE_DATE)))
                .extracting(Invoice::id)
                .containsExactly(expected.id());
    }

    @Test
    @DisplayName("matches a statement date within the tolerance window on either side")
    void matchesWithinToleranceWindow() {
        invoiceFor(CARDHOLDER, 100_000);

        assertThat(useCase.search(query(CARDHOLDER, 100_000, DUE_DATE.minusDays(3)))).hasSize(1);
        assertThat(useCase.search(query(CARDHOLDER, 100_000, DUE_DATE.plusDays(3)))).hasSize(1);
    }

    @Test
    @DisplayName("does not match a statement date outside the tolerance window")
    void ignoresDatesOutsideTolerance() {
        invoiceFor(CARDHOLDER, 100_000);

        assertThat(useCase.search(query(CARDHOLDER, 100_000, DUE_DATE.plusDays(4)))).isEmpty();
        assertThat(useCase.search(query(CARDHOLDER, 100_000, DUE_DATE.minusDays(4)))).isEmpty();
    }

    @Test
    @DisplayName("does not match another cardholder's invoice for the same amount")
    void ignoresOtherCardholders() {
        invoiceFor(SOMEONE_ELSE, 100_000);

        assertThat(useCase.search(query(CARDHOLDER, 100_000, DUE_DATE))).isEmpty();
    }

    @Test
    @DisplayName("matches what the invoice currently owes, not the total it closed with")
    void matchesAmountOwedIncludingInterest() {
        Invoice invoice = invoiceFor(CARDHOLDER, 100_000);
        invoice.accrueInterest(InterestCharge.of(Money.ofCents(2_000), Money.ofCents(1_000)), DUE_DATE.plusDays(1));
        invoices.save(invoice);

        assertThat(useCase.search(query(CARDHOLDER, 100_000, DUE_DATE))).isEmpty();
        assertThat(useCase.search(query(CARDHOLDER, 103_000, DUE_DATE))).hasSize(1);
    }

    @Test
    @DisplayName("never returns an invoice that is already paid")
    void ignoresPaidInvoices() {
        Invoice invoice = invoiceFor(CARDHOLDER, 100_000);
        invoice.recordPayment(Money.ofCents(100_000), LocalDateTime.of(2026, 3, 24, 10, 0),
                Payment.Source.INTERNAL, null);
        invoices.save(invoice);

        assertThat(useCase.search(query(CARDHOLDER, 100_000, DUE_DATE))).isEmpty();
    }

    @Test
    @DisplayName("a zero tolerance matches only the exact due date")
    void zeroToleranceMatchesExactDateOnly() {
        invoiceFor(CARDHOLDER, 100_000);

        assertThat(useCase.search(new InvoiceSearchQuery(CARDHOLDER, Money.ofCents(100_000), DUE_DATE, 0)))
                .hasSize(1);
        assertThat(useCase.search(
                new InvoiceSearchQuery(CARDHOLDER, Money.ofCents(100_000), DUE_DATE.plusDays(1), 0)))
                .isEmpty();
    }
}

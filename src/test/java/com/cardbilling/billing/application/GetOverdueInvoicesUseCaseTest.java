package com.cardbilling.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.billing.domain.BillingCycle;
import com.cardbilling.billing.domain.Cardholder;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.domain.Payment;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetOverdueInvoicesUseCaseTest {

    private static final Cardholder CARDHOLDER = Cardholder.of(7L, DocumentNumber.of("10000000042"));
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 25);

    private InMemoryInvoiceRepository invoices;
    private GetOverdueInvoicesUseCase useCase;

    @BeforeEach
    void setUp() {
        invoices = new InMemoryInvoiceRepository();
        useCase = new GetOverdueInvoicesUseCase(invoices);
    }

    private Invoice closedInvoice() {
        return invoices.save(Invoice.close(1L, CARDHOLDER, BillingCycle.closingOn(LocalDate.of(2026, 3, 15)),
                Money.ofCents(100_000)));
    }

    @Test
    @DisplayName("an invoice is not overdue on its due date, only after it")
    void notOverdueUntilAfterDueDate() {
        closedInvoice();

        assertThat(useCase.overdueAsOf(DUE_DATE)).isEmpty();
        assertThat(useCase.overdueAsOf(DUE_DATE.plusDays(1))).hasSize(1);
    }

    @Test
    @DisplayName("a paid invoice never appears, however late it was settled")
    void paidInvoicesAreExcluded() {
        Invoice invoice = closedInvoice();
        invoice.recordPayment(Money.ofCents(100_000), LocalDateTime.of(2026, 4, 30, 10, 0),
                Payment.Source.INTERNAL, null);
        invoices.save(invoice);

        assertThat(useCase.overdueAsOf(DUE_DATE.plusDays(30))).isEmpty();
    }
}

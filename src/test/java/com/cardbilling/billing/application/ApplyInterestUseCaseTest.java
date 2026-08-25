package com.cardbilling.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardbilling.billing.domain.BillingCycle;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.InterestPolicy;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.InvoiceNotFoundException;
import com.cardbilling.billing.domain.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplyInterestUseCaseTest {

    private static final LocalDate CLOSING_DATE = LocalDate.of(2026, 3, 15);
    private static final LocalDate ACCRUAL_DATE = LocalDate.of(2026, 3, 26);

    private InMemoryInvoiceRepository invoices;
    private ApplyInterestUseCase useCase;
    private long invoiceId;

    @BeforeEach
    void setUp() {
        invoices = new InMemoryInvoiceRepository();
        useCase = new ApplyInterestUseCase(invoices, new InterestPolicy());
        invoiceId = invoices.save(Invoice.close(1L, DocumentNumber.of("10000000042"),
                BillingCycle.closingOn(CLOSING_DATE), Money.ofCents(100_000))).id();
    }

    @Test
    @DisplayName("computes the charge from this service's own policy when the caller omits amounts")
    void computesChargeFromPolicyWhenAmountsOmitted() {
        InterestApplicationResult result = useCase.apply(
                ApplyInterestCommand.computedByPolicy(invoiceId, ACCRUAL_DATE));

        assertThat(result.applied()).isTrue();
        // 2% late fee once plus 1% daily, both on the original 100000.
        assertThat(result.invoice().interestApplied()).isEqualTo(Money.ofCents(3_000));
        assertThat(result.invoice().status()).isEqualTo(Invoice.Status.OVERDUE);
    }

    @Test
    @DisplayName("applies the amounts the caller computed when it supplies them")
    void appliesCallerSuppliedAmounts() {
        InterestApplicationResult result = useCase.apply(new ApplyInterestCommand(
                invoiceId, Money.ofCents(1_500), Money.ofCents(750), ACCRUAL_DATE));

        assertThat(result.invoice().interestApplied()).isEqualTo(Money.ofCents(2_250));
    }

    @Test
    @DisplayName("a second request for the same day changes nothing and says so")
    void secondRequestForSameDayIsANoOp() {
        useCase.apply(ApplyInterestCommand.computedByPolicy(invoiceId, ACCRUAL_DATE));

        InterestApplicationResult replay = useCase.apply(
                ApplyInterestCommand.computedByPolicy(invoiceId, ACCRUAL_DATE));

        assertThat(replay.applied()).isFalse();
        assertThat(replay.invoice().interestApplied()).isEqualTo(Money.ofCents(3_000));
        assertThat(replay.invoice().interestAccruals()).hasSize(1);
    }

    @Test
    @DisplayName("charges the late fee once but daily interest every day")
    void chargesLateFeeOnceAcrossDays() {
        useCase.apply(ApplyInterestCommand.computedByPolicy(invoiceId, ACCRUAL_DATE));
        InterestApplicationResult secondDay = useCase.apply(
                ApplyInterestCommand.computedByPolicy(invoiceId, ACCRUAL_DATE.plusDays(1)));

        // 2000 fee + 1000 + 1000 daily.
        assertThat(secondDay.invoice().interestApplied()).isEqualTo(Money.ofCents(4_000));
        assertThat(secondDay.invoice().interestAccruals()).hasSize(2);
    }

    @Test
    @DisplayName("rejects an unknown invoice rather than silently doing nothing")
    void rejectsUnknownInvoice() {
        assertThatThrownBy(() -> useCase.apply(ApplyInterestCommand.computedByPolicy(9999L, ACCRUAL_DATE)))
                .isInstanceOf(InvoiceNotFoundException.class);
    }
}

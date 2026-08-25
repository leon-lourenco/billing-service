package com.cardbilling.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardbilling.billing.domain.BillingCycle;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.InvoiceNotFoundException;
import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.domain.Payment;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordPaymentUseCaseTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 3, 24, 10, 0);

    private InMemoryInvoiceRepository invoices;
    private RecordPaymentUseCase useCase;
    private long invoiceId;

    @BeforeEach
    void setUp() {
        invoices = new InMemoryInvoiceRepository();
        useCase = new RecordPaymentUseCase(invoices);
        invoiceId = invoices.save(Invoice.close(1L, DocumentNumber.of("10000000042"),
                BillingCycle.closingOn(LocalDate.of(2026, 3, 15)), Money.ofCents(100_000))).id();
    }

    private RecordPaymentCommand reconciledPayment(long cents, String reference) {
        return new RecordPaymentCommand(invoiceId, Money.ofCents(cents),
                Payment.Source.EXTERNAL_RECONCILIATION, reference, PAID_AT);
    }

    @Test
    @DisplayName("a payment covering the balance marks the invoice paid")
    void fullPaymentMarksInvoicePaid() {
        PaymentRecordingResult result = useCase.record(reconciledPayment(100_000, "STMT-0001"));

        assertThat(result.recorded()).isTrue();
        assertThat(result.invoice().isPaid()).isTrue();
        assertThat(result.invoice().amountDue()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("a partial payment leaves the invoice open with the remainder due")
    void partialPaymentLeavesInvoiceOpen() {
        PaymentRecordingResult result = useCase.record(reconciledPayment(40_000, "STMT-0001"));

        assertThat(result.invoice().isPaid()).isFalse();
        assertThat(result.invoice().amountDue()).isEqualTo(Money.ofCents(60_000));
    }

    @Test
    @DisplayName("replaying a reconciliation match records nothing a second time")
    void replayedMatchIsANoOp() {
        useCase.record(reconciledPayment(100_000, "STMT-0001"));

        PaymentRecordingResult replay = useCase.record(reconciledPayment(100_000, "STMT-0001"));

        assertThat(replay.recorded()).isFalse();
        assertThat(replay.invoice().payments()).hasSize(1);
    }

    @Test
    @DisplayName("a statement line already matched elsewhere is not recorded again on another invoice")
    void externalReferenceIsUniqueAcrossInvoices() {
        useCase.record(reconciledPayment(100_000, "STMT-0001"));
        long otherInvoiceId = invoices.save(Invoice.close(2L, DocumentNumber.of("10000000043"),
                BillingCycle.closingOn(LocalDate.of(2026, 3, 15)), Money.ofCents(100_000))).id();

        PaymentRecordingResult result = useCase.record(new RecordPaymentCommand(otherInvoiceId,
                Money.ofCents(100_000), Payment.Source.EXTERNAL_RECONCILIATION, "STMT-0001", PAID_AT));

        assertThat(result.recorded()).isFalse();
        assertThat(result.invoice().payments()).isEmpty();
    }

    @Test
    @DisplayName("internal payments without a reference are always recorded")
    void internalPaymentsAreAlwaysRecorded() {
        useCase.record(new RecordPaymentCommand(invoiceId, Money.ofCents(30_000),
                Payment.Source.INTERNAL, null, PAID_AT));
        PaymentRecordingResult second = useCase.record(new RecordPaymentCommand(invoiceId,
                Money.ofCents(30_000), Payment.Source.INTERNAL, null, PAID_AT));

        assertThat(second.recorded()).isTrue();
        assertThat(second.invoice().payments()).hasSize(2);
        assertThat(second.invoice().amountDue()).isEqualTo(Money.ofCents(40_000));
    }

    @Test
    @DisplayName("rejects a payment against an unknown invoice")
    void rejectsUnknownInvoice() {
        assertThatThrownBy(() -> useCase.record(new RecordPaymentCommand(9999L, Money.ofCents(100),
                Payment.Source.INTERNAL, null, PAID_AT)))
                .isInstanceOf(InvoiceNotFoundException.class);
    }
}

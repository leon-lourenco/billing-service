package com.cardbilling.billing.infrastructure.web;

import com.cardbilling.billing.application.RecordPaymentCommand;
import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.domain.Payment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

@Schema(description = "A payment received against an invoice")
public record RecordPaymentRequest(
        @NotNull(message = "amountCents is required")
        @Positive(message = "amountCents must be positive")
        Long amountCents,

        @NotNull(message = "source is required")
        Payment.Source source,

        @Schema(description = """
                The statement line this payment came from. Required for a reconciled payment, and \
                what makes replaying the same match a no-op.""")
        String externalReference,

        @NotNull(message = "paidAt is required")
        LocalDateTime paidAt) {

    RecordPaymentCommand toCommand(long invoiceId) {
        return new RecordPaymentCommand(invoiceId, Money.ofCents(amountCents), source, externalReference, paidAt);
    }
}

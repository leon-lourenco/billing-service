package com.cardbilling.billing.application;

import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.domain.Payment;
import java.time.LocalDateTime;
import java.util.Objects;

public record RecordPaymentCommand(
        long invoiceId,
        Money amount,
        Payment.Source source,
        String externalReference,
        LocalDateTime paidAt) {

    public RecordPaymentCommand {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(paidAt, "paidAt");
    }

    public boolean hasExternalReference() {
        return externalReference != null && !externalReference.isBlank();
    }
}

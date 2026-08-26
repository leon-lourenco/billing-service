package com.cardbilling.billing.infrastructure.web;

import com.cardbilling.billing.application.PaymentRecordingResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentRecordingResponse(
        @Schema(description = """
                False when a payment for this statement line was already on file - the \
                reconciliation match was replayed and no second payment was created.""")
        boolean recorded,
        InvoiceResponse invoice) {

    static PaymentRecordingResponse from(PaymentRecordingResult result) {
        return new PaymentRecordingResponse(result.recorded(), InvoiceResponse.from(result.invoice()));
    }
}

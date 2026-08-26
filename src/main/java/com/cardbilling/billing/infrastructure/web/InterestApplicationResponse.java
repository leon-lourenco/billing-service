package com.cardbilling.billing.infrastructure.web;

import com.cardbilling.billing.application.InterestApplicationResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record InterestApplicationResponse(
        @Schema(description = """
                False when this invoice had already been charged for that day - the request was a \
                replay, nothing changed, and the invoice below is the state that was already there.""")
        boolean applied,
        InvoiceResponse invoice) {

    static InterestApplicationResponse from(InterestApplicationResult result) {
        return new InterestApplicationResponse(result.applied(), InvoiceResponse.from(result.invoice()));
    }
}

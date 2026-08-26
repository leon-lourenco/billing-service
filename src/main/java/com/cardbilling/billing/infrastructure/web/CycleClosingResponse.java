package com.cardbilling.billing.infrastructure.web;

import com.cardbilling.billing.application.CycleClosingSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record CycleClosingResponse(
        LocalDate closingDate,
        @Schema(description = "Active cards whose cycle day matched this date")
        int cardsConsidered,
        @Schema(description = "Invoices actually closed - a card with nothing to bill produces none")
        int invoicesClosed,
        long totalBilledCents) {

    static CycleClosingResponse from(CycleClosingSummary summary) {
        return new CycleClosingResponse(summary.closingDate(), summary.cardsConsidered(),
                summary.invoicesClosed(), summary.totalBilled().cents());
    }
}

package com.cardbilling.billing.infrastructure.web;

import com.cardbilling.billing.domain.Invoice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "An invoice and what is currently owed on it")
public record InvoiceResponse(
        Long id,
        long cardId,
        String documentNumber,
        String referenceMonth,
        LocalDate closingDate,
        LocalDate dueDate,
        @Schema(description = "The cycle total the invoice closed with")
        long totalAmountCents,
        @Schema(description = "Interest and late fees charged since closing")
        long interestAppliedCents,
        @Schema(description = "Total plus interest - the figure a statement line is matched against")
        long amountOwedCents,
        long amountPaidCents,
        long amountDueCents,
        LocalDate lastInterestAccrualDate,
        Invoice.Status status) {

    static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.id(),
                invoice.cardId(),
                invoice.customerDocumentNumber().value(),
                invoice.referenceMonth(),
                invoice.closingDate(),
                invoice.dueDate(),
                invoice.totalAmount().cents(),
                invoice.interestApplied().cents(),
                invoice.amountOwed().cents(),
                invoice.amountPaid().cents(),
                invoice.amountDue().cents(),
                invoice.lastInterestAccrualDate().orElse(null),
                invoice.status());
    }
}

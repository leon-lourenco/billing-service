package com.cardbilling.billing.application;

import com.cardbilling.billing.domain.Money;
import java.time.LocalDate;

/**
 * What a closing run actually did. Reports cards skipped for having nothing to bill separately
 * from invoices closed, because "closed 0 invoices" and "no card closes today" are different
 * situations and the legacy's single count couldn't tell them apart.
 */
public record CycleClosingSummary(
        LocalDate closingDate,
        int cardsConsidered,
        int invoicesClosed,
        Money totalBilled) {

    public static CycleClosingSummary of(LocalDate closingDate, int cardsConsidered, int invoicesClosed,
            Money totalBilled) {
        return new CycleClosingSummary(closingDate, cardsConsidered, invoicesClosed, totalBilled);
    }
}

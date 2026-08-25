package com.cardbilling.billing.application;

import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * "Is there an unpaid invoice for this cardholder, owing exactly this much, falling due within a
 * few days of this date?" - the question a bank statement line asks, phrased so a single index
 * can answer it.
 *
 * <p>The amount is matched against what the invoice currently owes (its closed total plus
 * interest accrued since), not against the total it closed with, because that is what the
 * cardholder was actually asked to pay.
 */
public record InvoiceSearchQuery(
        DocumentNumber documentNumber,
        Money amountOwed,
        LocalDate aroundDate,
        int toleranceDays) {

    public static final int DEFAULT_TOLERANCE_DAYS = 3;

    public InvoiceSearchQuery {
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(amountOwed, "amountOwed");
        Objects.requireNonNull(aroundDate, "aroundDate");
        if (toleranceDays < 0) {
            throw new IllegalArgumentException("Tolerance in days must not be negative, got: " + toleranceDays);
        }
    }

    public LocalDate earliestDueDate() {
        return aroundDate.minusDays(toleranceDays);
    }

    public LocalDate latestDueDate() {
        return aroundDate.plusDays(toleranceDays);
    }
}

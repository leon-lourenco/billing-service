package com.cardbilling.billing.application;

import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.MalformedValueException;
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
 *
 * <p>{@code amountOwed} may be {@code null}: a caller that already knows the exact-amount lookup
 * found nothing asks this same question without it, to tell "this customer owes something else in
 * the window" apart from "this customer owes nothing in the window at all".
 */
public record InvoiceSearchQuery(
        DocumentNumber documentNumber,
        Money amountOwed,
        LocalDate aroundDate,
        int toleranceDays) {

    public static final int DEFAULT_TOLERANCE_DAYS = 3;

    public InvoiceSearchQuery {
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(aroundDate, "aroundDate");
        if (toleranceDays < 0) {
            throw new MalformedValueException("toleranceDays",
                    "Tolerance in days must not be negative, got: " + toleranceDays);
        }
    }

    public LocalDate earliestDueDate() {
        return aroundDate.minusDays(toleranceDays);
    }

    public LocalDate latestDueDate() {
        return aroundDate.plusDays(toleranceDays);
    }
}

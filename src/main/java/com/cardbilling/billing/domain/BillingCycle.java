package com.cardbilling.billing.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * The billing cycle that closes on a given date: which transactions belong to it, what reference
 * month it is filed under, and when it falls due.
 *
 * <p>The window is {@code [closingDate - 1 month, closingDate)} - the closing day itself belongs
 * to the next cycle, so a purchase made on closing day is not billed on an invoice the customer
 * receives that same day. Ten days between closing and due date, matching the legacy monolith
 * and typical card billing.
 */
public record BillingCycle(LocalDate closingDate) {

    public static final int DAYS_UNTIL_DUE = 10;

    private static final DateTimeFormatter REFERENCE_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    public BillingCycle {
        Objects.requireNonNull(closingDate, "closingDate");
    }

    public static BillingCycle closingOn(LocalDate closingDate) {
        return new BillingCycle(closingDate);
    }

    /** Inclusive lower bound of the transactions this cycle bills. */
    public LocalDateTime windowStart() {
        return closingDate.minusMonths(1).atStartOfDay();
    }

    /** Exclusive upper bound of the transactions this cycle bills. */
    public LocalDateTime windowEnd() {
        return closingDate.atStartOfDay();
    }

    public LocalDate dueDate() {
        return closingDate.plusDays(DAYS_UNTIL_DUE);
    }

    public String referenceMonth() {
        return closingDate.format(REFERENCE_MONTH);
    }

    /** Whether this card's cycle is the one closing today. */
    public boolean closes(Card card) {
        return card.billingCycleDay() == closingDate.getDayOfMonth();
    }
}

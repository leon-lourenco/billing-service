package com.cardbilling.billing.domain;

import java.util.Objects;

/**
 * What a single day's interest accrual costs, split into its two components so an invoice can
 * show a customer why the balance moved: a one-off late fee charged the first time the invoice
 * goes overdue, and the daily interest charged every day thereafter.
 */
public record InterestCharge(Money lateFee, Money dailyInterest) {

    public InterestCharge {
        Objects.requireNonNull(lateFee, "lateFee");
        Objects.requireNonNull(dailyInterest, "dailyInterest");
        if (lateFee.cents() < 0 || dailyInterest.cents() < 0) {
            throw new IllegalArgumentException("Interest charge components must not be negative");
        }
    }

    public static InterestCharge of(Money lateFee, Money dailyInterest) {
        return new InterestCharge(lateFee, dailyInterest);
    }

    public Money total() {
        return lateFee.plus(dailyInterest);
    }
}

package com.cardbilling.billing.application;

import com.cardbilling.billing.domain.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A request to charge one day's interest on an invoice.
 *
 * <p>The fee and daily interest are optional. When the caller supplies them - which is what
 * {@code collections-service} does, having computed them from the overdue set it just read -
 * they are applied as given. When they are omitted, this service computes them from its own
 * {@code InterestPolicy}, so a caller that would rather not reimplement the rule doesn't have to.
 * Either way the idempotency guard is enforced here, not by the caller.
 */
public record ApplyInterestCommand(
        long invoiceId,
        Money lateFee,
        Money dailyInterest,
        LocalDate accrualDate) {

    public ApplyInterestCommand {
        Objects.requireNonNull(accrualDate, "accrualDate");
    }

    public static ApplyInterestCommand computedByPolicy(long invoiceId, LocalDate accrualDate) {
        return new ApplyInterestCommand(invoiceId, null, null, accrualDate);
    }

    /** Whether the caller told us what to charge, rather than leaving it to the policy. */
    public boolean specifiesAmounts() {
        return lateFee != null || dailyInterest != null;
    }

    public Optional<Money> optionalLateFee() {
        return Optional.ofNullable(lateFee);
    }

    public Optional<Money> optionalDailyInterest() {
        return Optional.ofNullable(dailyInterest);
    }
}

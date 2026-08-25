package com.cardbilling.billing.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One day's interest applied to one invoice.
 *
 * <p>The legacy monolith kept only a {@code lastInterestAccrualDate} on the invoice, which made
 * "have we already charged for this day" answerable for exactly one day - the most recent one.
 * Recording each accrual as its own fact makes the question answerable for any day, and gives
 * the persistence layer something to put a unique constraint on, so a duplicate accrual is
 * rejected by the database rather than by whichever caller happened to remember to check.
 */
public class InterestAccrual {

    private final Long id;
    private final LocalDate accrualDate;
    private final InterestCharge charge;

    private InterestAccrual(Long id, LocalDate accrualDate, InterestCharge charge) {
        this.id = id;
        this.accrualDate = Objects.requireNonNull(accrualDate, "accrualDate");
        this.charge = Objects.requireNonNull(charge, "charge");
    }

    static InterestAccrual on(LocalDate accrualDate, InterestCharge charge) {
        return new InterestAccrual(null, accrualDate, charge);
    }

    public static InterestAccrual reconstitute(Long id, LocalDate accrualDate, InterestCharge charge) {
        return new InterestAccrual(id, accrualDate, charge);
    }

    public Long id() {
        return id;
    }

    public LocalDate accrualDate() {
        return accrualDate;
    }

    public InterestCharge charge() {
        return charge;
    }

    public Money amount() {
        return charge.total();
    }
}

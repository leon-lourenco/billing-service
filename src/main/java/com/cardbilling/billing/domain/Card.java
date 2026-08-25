package com.cardbilling.billing.domain;

import java.util.Objects;

/**
 * A card on an account. Its {@code billingCycleDay} is the day of the month its cycle closes on,
 * which is what spreads invoice closing across the month instead of stacking every invoice on
 * the first.
 */
public class Card {

    public enum Status {
        ACTIVE, BLOCKED, CANCELLED
    }

    private final Long id;
    private final long accountId;
    private final String cardNumberMasked;
    private final Money creditLimit;
    private final int billingCycleDay;
    private final Status status;

    private Card(Long id, long accountId, String cardNumberMasked, Money creditLimit, int billingCycleDay,
            Status status) {
        this.id = id;
        this.accountId = accountId;
        this.cardNumberMasked = Objects.requireNonNull(cardNumberMasked, "cardNumberMasked");
        this.creditLimit = Objects.requireNonNull(creditLimit, "creditLimit");
        if (billingCycleDay < 1 || billingCycleDay > 28) {
            throw new IllegalArgumentException(
                    "Billing cycle day must be between 1 and 28 so every month has one, got: " + billingCycleDay);
        }
        this.billingCycleDay = billingCycleDay;
        this.status = Objects.requireNonNull(status, "status");
    }

    public static Card issue(long accountId, String cardNumberMasked, Money creditLimit, int billingCycleDay) {
        return new Card(null, accountId, cardNumberMasked, creditLimit, billingCycleDay, Status.ACTIVE);
    }

    public static Card reconstitute(Long id, long accountId, String cardNumberMasked, Money creditLimit,
            int billingCycleDay, Status status) {
        return new Card(id, accountId, cardNumberMasked, creditLimit, billingCycleDay, status);
    }

    public Card withId(Long assignedId) {
        return new Card(assignedId, accountId, cardNumberMasked, creditLimit, billingCycleDay, status);
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public Long id() {
        return id;
    }

    public long accountId() {
        return accountId;
    }

    public String cardNumberMasked() {
        return cardNumberMasked;
    }

    public Money creditLimit() {
        return creditLimit;
    }

    public int billingCycleDay() {
        return billingCycleDay;
    }

    public Status status() {
        return status;
    }
}

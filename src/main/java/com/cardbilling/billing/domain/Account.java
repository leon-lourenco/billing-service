package com.cardbilling.billing.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** A customer's account. One customer may hold several; each holds one or more cards. */
public class Account {

    public enum Status {
        ACTIVE, SUSPENDED, CLOSED
    }

    private final Long id;
    private final long customerId;
    private final String accountNumber;
    private final Status status;
    private final LocalDateTime openedAt;

    private Account(Long id, long customerId, String accountNumber, Status status, LocalDateTime openedAt) {
        this.id = id;
        this.customerId = customerId;
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
        this.status = Objects.requireNonNull(status, "status");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
    }

    public static Account open(long customerId, String accountNumber, LocalDateTime openedAt) {
        return new Account(null, customerId, accountNumber, Status.ACTIVE, openedAt);
    }

    public static Account reconstitute(Long id, long customerId, String accountNumber, Status status,
            LocalDateTime openedAt) {
        return new Account(id, customerId, accountNumber, status, openedAt);
    }

    public Account withId(Long assignedId) {
        return new Account(assignedId, customerId, accountNumber, status, openedAt);
    }

    public Long id() {
        return id;
    }

    public long customerId() {
        return customerId;
    }

    public String accountNumber() {
        return accountNumber;
    }

    public Status status() {
        return status;
    }

    public LocalDateTime openedAt() {
        return openedAt;
    }
}

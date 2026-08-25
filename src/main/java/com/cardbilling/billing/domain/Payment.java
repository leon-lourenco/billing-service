package com.cardbilling.billing.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Money received against an invoice.
 *
 * <p>An {@code externalReference} is set when the payment came from a reconciled bank statement
 * line - it is that line's own identifier, and it is what makes replaying a reconciliation run
 * safe: the same statement line can only ever produce one payment.
 */
public class Payment {

    public enum Source {
        INTERNAL, EXTERNAL_RECONCILIATION
    }

    private final Long id;
    private final Money amount;
    private final LocalDateTime paidAt;
    private final Source source;
    private final String externalReference;

    private Payment(Long id, Money amount, LocalDateTime paidAt, Source source, String externalReference) {
        this.id = id;
        this.amount = Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("A payment must be for a positive amount, got: " + amount);
        }
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
        this.source = Objects.requireNonNull(source, "source");
        if (source == Source.EXTERNAL_RECONCILIATION && (externalReference == null || externalReference.isBlank())) {
            throw new IllegalArgumentException(
                    "A reconciled payment must carry the statement line's external reference");
        }
        this.externalReference = externalReference;
    }

    static Payment of(Money amount, LocalDateTime paidAt, Source source, String externalReference) {
        return new Payment(null, amount, paidAt, source, externalReference);
    }

    public static Payment reconstitute(Long id, Money amount, LocalDateTime paidAt, Source source,
            String externalReference) {
        return new Payment(id, amount, paidAt, source, externalReference);
    }

    public Long id() {
        return id;
    }

    public Money amount() {
        return amount;
    }

    public LocalDateTime paidAt() {
        return paidAt;
    }

    public Source source() {
        return source;
    }

    public Optional<String> externalReference() {
        return Optional.ofNullable(externalReference);
    }
}

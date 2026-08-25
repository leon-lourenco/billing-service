package com.cardbilling.billing.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * A purchase on a card. Carries no invoice until cycle closing aggregates it into one - that
 * null {@code invoiceId} is exactly what "not yet billed" means, and it is what stops a
 * transaction from ever landing on two invoices.
 */
public class CardTransaction {

    private final Long id;
    private final long cardId;
    private final Long invoiceId;
    private final String merchantName;
    private final Money amount;
    private final LocalDateTime transactionDate;

    private CardTransaction(Long id, long cardId, Long invoiceId, String merchantName, Money amount,
            LocalDateTime transactionDate) {
        this.id = id;
        this.cardId = cardId;
        this.invoiceId = invoiceId;
        this.merchantName = Objects.requireNonNull(merchantName, "merchantName");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.transactionDate = Objects.requireNonNull(transactionDate, "transactionDate");
    }

    public static CardTransaction record(long cardId, String merchantName, Money amount,
            LocalDateTime transactionDate) {
        return new CardTransaction(null, cardId, null, merchantName, amount, transactionDate);
    }

    public static CardTransaction reconstitute(Long id, long cardId, Long invoiceId, String merchantName,
            Money amount, LocalDateTime transactionDate) {
        return new CardTransaction(id, cardId, invoiceId, merchantName, amount, transactionDate);
    }

    public CardTransaction assignedTo(Invoice invoice) {
        if (this.invoiceId != null) {
            throw new IllegalStateException(
                    "Transaction " + id + " is already billed on invoice " + invoiceId);
        }
        Long assignedInvoiceId = Objects.requireNonNull(invoice.id(),
                "Invoice must be persisted before transactions can be assigned to it");
        return new CardTransaction(id, cardId, assignedInvoiceId, merchantName, amount, transactionDate);
    }

    public boolean isBilled() {
        return invoiceId != null;
    }

    public Long id() {
        return id;
    }

    public long cardId() {
        return cardId;
    }

    public Optional<Long> invoiceId() {
        return Optional.ofNullable(invoiceId);
    }

    public String merchantName() {
        return merchantName;
    }

    public Money amount() {
        return amount;
    }

    public LocalDateTime transactionDate() {
        return transactionDate;
    }
}

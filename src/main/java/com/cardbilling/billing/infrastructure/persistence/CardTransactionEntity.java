package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.domain.CardTransaction;
import com.cardbilling.billing.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "card_transactions", indexes = {
        // The cycle-closing lookup: unbilled transactions on one card within the cycle window.
        @Index(name = "idx_transactions_unbilled", columnList = "card_id, invoice_id, transaction_date"),
        @Index(name = "idx_transactions_invoice", columnList = "invoice_id")
})
public class CardTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    /** Null until cycle closing claims this transaction for an invoice. */
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "merchant_name", nullable = false)
    private String merchantName;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    protected CardTransactionEntity() {
    }

    static CardTransactionEntity fromDomain(CardTransaction transaction) {
        CardTransactionEntity entity = new CardTransactionEntity();
        entity.id = transaction.id();
        entity.cardId = transaction.cardId();
        entity.invoiceId = transaction.invoiceId().orElse(null);
        entity.merchantName = transaction.merchantName();
        entity.amountCents = transaction.amount().cents();
        entity.transactionDate = transaction.transactionDate();
        return entity;
    }

    CardTransaction toDomain() {
        return CardTransaction.reconstitute(id, cardId, invoiceId, merchantName, Money.ofCents(amountCents),
                transactionDate);
    }
}

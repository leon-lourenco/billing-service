package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.domain.Card;
import com.cardbilling.billing.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "cards", indexes = {
        @Index(name = "idx_cards_account", columnList = "account_id"),
        // Invoice closing asks for exactly this every time it runs: active cards on today's cycle day.
        @Index(name = "idx_cards_cycle_day", columnList = "status, billing_cycle_day")
})
public class CardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "card_number_masked", nullable = false)
    private String cardNumberMasked;

    @Column(name = "credit_limit_cents", nullable = false)
    private long creditLimitCents;

    /** Day of month the billing cycle closes on, e.g. 5 means invoices close on the 5th. */
    @Column(name = "billing_cycle_day", nullable = false)
    private int billingCycleDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Card.Status status;

    protected CardEntity() {
    }

    static CardEntity fromDomain(Card card) {
        CardEntity entity = new CardEntity();
        entity.id = card.id();
        entity.accountId = card.accountId();
        entity.cardNumberMasked = card.cardNumberMasked();
        entity.creditLimitCents = card.creditLimit().cents();
        entity.billingCycleDay = card.billingCycleDay();
        entity.status = card.status();
        return entity;
    }

    Card toDomain() {
        return Card.reconstitute(id, accountId, cardNumberMasked, Money.ofCents(creditLimitCents),
                billingCycleDay, status);
    }
}

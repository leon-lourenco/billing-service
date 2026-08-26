package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.application.BillableCard;
import com.cardbilling.billing.domain.DocumentNumber;

/** The row shape the cycle-closing query projects into, before it becomes a domain-facing pair. */
public record BillableCardRow(CardEntity card, String cardholderDocumentNumber) {

    BillableCard toDomain() {
        return new BillableCard(card.toDomain(), DocumentNumber.of(cardholderDocumentNumber));
    }
}

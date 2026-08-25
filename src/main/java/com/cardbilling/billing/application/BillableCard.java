package com.cardbilling.billing.application;

import com.cardbilling.billing.domain.Card;
import com.cardbilling.billing.domain.DocumentNumber;

/**
 * A card together with its cardholder's document number - what invoice closing needs to stamp a
 * new invoice with the key {@code /invoices/search} later looks it up by.
 */
public record BillableCard(Card card, DocumentNumber cardholderDocument) {

    public long cardId() {
        return card.id();
    }
}

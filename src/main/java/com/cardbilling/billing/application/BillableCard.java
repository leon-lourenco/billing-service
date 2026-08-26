package com.cardbilling.billing.application;

import com.cardbilling.billing.domain.Card;
import com.cardbilling.billing.domain.Cardholder;

/**
 * A card together with the cardholder it belongs to - what invoice closing needs to stamp a new
 * invoice with the id and document number callers later look it up and act on it by.
 */
public record BillableCard(Card card, Cardholder cardholder) {

    public long cardId() {
        return card.id();
    }
}

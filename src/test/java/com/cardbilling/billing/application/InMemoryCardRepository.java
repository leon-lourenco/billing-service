package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.CardRepositoryPort;
import com.cardbilling.billing.domain.Card;
import com.cardbilling.billing.domain.DocumentNumber;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

class InMemoryCardRepository implements CardRepositoryPort {

    private final Map<Long, Card> cards = new LinkedHashMap<>();
    private final Map<Long, DocumentNumber> cardholders = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Card save(Card card) {
        Card stored = card.id() == null ? card.withId(nextId.getAndIncrement()) : card;
        cards.put(stored.id(), stored);
        return stored;
    }

    /** Registers a card already paired with its cardholder, the way the closing query returns it. */
    Card saveHeldBy(Card card, DocumentNumber cardholderDocument) {
        Card stored = save(card);
        cardholders.put(stored.id(), cardholderDocument);
        return stored;
    }

    @Override
    public List<BillableCard> findActiveCardsClosingOn(int dayOfMonth) {
        List<BillableCard> matching = new ArrayList<>();
        for (Card card : cards.values()) {
            if (card.isActive() && card.billingCycleDay() == dayOfMonth) {
                matching.add(new BillableCard(card, cardholders.get(card.id())));
            }
        }
        return matching;
    }

    long count() {
        return cards.size();
    }

    List<Card> all() {
        return new ArrayList<>(cards.values());
    }
}

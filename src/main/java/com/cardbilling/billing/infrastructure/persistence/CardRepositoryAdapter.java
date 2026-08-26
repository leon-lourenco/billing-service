package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.application.BillableCard;
import com.cardbilling.billing.application.port.CardRepositoryPort;
import com.cardbilling.billing.domain.Card;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class CardRepositoryAdapter implements CardRepositoryPort {

    private final CardJpaRepository cards;

    CardRepositoryAdapter(CardJpaRepository cards) {
        this.cards = cards;
    }

    @Override
    public Card save(Card card) {
        return cards.save(CardEntity.fromDomain(card)).toDomain();
    }

    @Override
    public List<BillableCard> findActiveCardsClosingOn(int dayOfMonth) {
        return cards.findActiveCardsClosingOn(Card.Status.ACTIVE, dayOfMonth).stream()
                .map(BillableCardRow::toDomain)
                .toList();
    }
}

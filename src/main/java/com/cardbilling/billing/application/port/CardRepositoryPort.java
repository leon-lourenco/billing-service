package com.cardbilling.billing.application.port;

import com.cardbilling.billing.application.BillableCard;
import com.cardbilling.billing.domain.Card;
import java.util.List;

public interface CardRepositoryPort {

    Card save(Card card);

    /**
     * Active cards whose billing cycle closes on the given day of the month, each already paired
     * with its cardholder's document number.
     *
     * <p>The legacy loaded every active card and filtered by cycle day in Java, then walked
     * card - account - customer per invoice to find the document number. Both are answered here in
     * one query, because the closing job is the only place that ever needs this pairing and doing
     * it per card is how a 150-customer demo turns into a few hundred needless round trips.
     */
    List<BillableCard> findActiveCardsClosingOn(int dayOfMonth);
}

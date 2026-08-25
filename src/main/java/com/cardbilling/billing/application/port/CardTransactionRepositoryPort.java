package com.cardbilling.billing.application.port;

import com.cardbilling.billing.domain.CardTransaction;
import java.time.LocalDateTime;
import java.util.List;

public interface CardTransactionRepositoryPort {

    CardTransaction save(CardTransaction transaction);

    List<CardTransaction> saveAll(List<CardTransaction> transactions);

    /**
     * Transactions on this card that no invoice has claimed yet, within the cycle window.
     * Lower bound inclusive, upper bound exclusive.
     */
    List<CardTransaction> findUnbilledForCardBetween(long cardId, LocalDateTime from, LocalDateTime until);
}

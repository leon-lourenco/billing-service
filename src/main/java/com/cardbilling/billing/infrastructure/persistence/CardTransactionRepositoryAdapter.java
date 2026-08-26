package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.application.port.CardTransactionRepositoryPort;
import com.cardbilling.billing.domain.CardTransaction;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class CardTransactionRepositoryAdapter implements CardTransactionRepositoryPort {

    private final CardTransactionJpaRepository transactions;

    CardTransactionRepositoryAdapter(CardTransactionJpaRepository transactions) {
        this.transactions = transactions;
    }

    @Override
    public CardTransaction save(CardTransaction transaction) {
        return transactions.save(CardTransactionEntity.fromDomain(transaction)).toDomain();
    }

    @Override
    public List<CardTransaction> saveAll(List<CardTransaction> toSave) {
        return transactions.saveAll(toSave.stream().map(CardTransactionEntity::fromDomain).toList()).stream()
                .map(CardTransactionEntity::toDomain)
                .toList();
    }

    @Override
    public List<CardTransaction> findUnbilledForCardBetween(long cardId, LocalDateTime from, LocalDateTime until) {
        return transactions
                .findByCardIdAndInvoiceIdIsNullAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                        cardId, from, until)
                .stream()
                .map(CardTransactionEntity::toDomain)
                .toList();
    }
}

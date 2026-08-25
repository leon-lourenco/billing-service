package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.CardTransactionRepositoryPort;
import com.cardbilling.billing.domain.CardTransaction;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

class InMemoryCardTransactionRepository implements CardTransactionRepositoryPort {

    private final Map<Long, CardTransaction> transactions = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public CardTransaction save(CardTransaction transaction) {
        CardTransaction stored = transaction.id() == null
                ? CardTransaction.reconstitute(nextId.getAndIncrement(), transaction.cardId(),
                        transaction.invoiceId().orElse(null), transaction.merchantName(), transaction.amount(),
                        transaction.transactionDate())
                : transaction;
        transactions.put(stored.id(), stored);
        return stored;
    }

    @Override
    public List<CardTransaction> saveAll(List<CardTransaction> toSave) {
        return toSave.stream().map(this::save).toList();
    }

    @Override
    public List<CardTransaction> findUnbilledForCardBetween(long cardId, LocalDateTime from, LocalDateTime until) {
        List<CardTransaction> matching = new ArrayList<>();
        for (CardTransaction transaction : transactions.values()) {
            if (transaction.cardId() != cardId || transaction.isBilled()) {
                continue;
            }
            LocalDateTime date = transaction.transactionDate();
            if (!date.isBefore(from) && date.isBefore(until)) {
                matching.add(transaction);
            }
        }
        return matching;
    }

    List<CardTransaction> all() {
        return new ArrayList<>(transactions.values());
    }
}

package com.cardbilling.billing.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CardTransactionJpaRepository extends JpaRepository<CardTransactionEntity, Long> {

    List<CardTransactionEntity> findByCardIdAndInvoiceIdIsNullAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
            Long cardId, LocalDateTime from, LocalDateTime until);
}

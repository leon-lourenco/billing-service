package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.domain.Card;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CardJpaRepository extends JpaRepository<CardEntity, Long> {

    /**
     * Active cards closing on this day of the month, each with its cardholder's document number,
     * in one query. The join to account and customer happens here precisely so it does not happen
     * per card while an invoice is being built.
     */
    @Query("""
            select new com.cardbilling.billing.infrastructure.persistence.BillableCardRow(card, customer.documentNumber)
            from CardEntity card
              join AccountEntity account on account.id = card.accountId
              join CustomerEntity customer on customer.id = account.customerId
            where card.status = :status
              and card.billingCycleDay = :dayOfMonth
            order by card.id
            """)
    List<BillableCardRow> findActiveCardsClosingOn(
            @Param("status") Card.Status status,
            @Param("dayOfMonth") int dayOfMonth);
}

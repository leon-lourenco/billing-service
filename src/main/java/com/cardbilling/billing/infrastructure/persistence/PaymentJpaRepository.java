package com.cardbilling.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    /**
     * Asked across every invoice rather than within one: a reconciliation run being replayed may
     * well aim its retry at a different invoice than it originally matched, and the statement
     * line still must not be paid twice.
     */
    boolean existsByExternalReference(String externalReference);
}

package com.cardbilling.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long> {
}

package com.cardbilling.billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {
}

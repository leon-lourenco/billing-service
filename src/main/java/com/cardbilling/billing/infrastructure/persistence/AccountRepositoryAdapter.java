package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.application.port.AccountRepositoryPort;
import com.cardbilling.billing.domain.Account;
import org.springframework.stereotype.Repository;

@Repository
class AccountRepositoryAdapter implements AccountRepositoryPort {

    private final AccountJpaRepository accounts;

    AccountRepositoryAdapter(AccountJpaRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Account save(Account account) {
        return accounts.save(AccountEntity.fromDomain(account)).toDomain();
    }
}

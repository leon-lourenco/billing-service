package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.AccountRepositoryPort;
import com.cardbilling.billing.domain.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class InMemoryAccountRepository implements AccountRepositoryPort {

    private final List<Account> accounts = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Account save(Account account) {
        Account stored = account.id() == null ? account.withId(nextId.getAndIncrement()) : account;
        accounts.add(stored);
        return stored;
    }

    List<Account> all() {
        return List.copyOf(accounts);
    }
}

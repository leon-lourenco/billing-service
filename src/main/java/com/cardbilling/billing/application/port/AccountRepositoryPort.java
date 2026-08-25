package com.cardbilling.billing.application.port;

import com.cardbilling.billing.domain.Account;

public interface AccountRepositoryPort {

    Account save(Account account);
}

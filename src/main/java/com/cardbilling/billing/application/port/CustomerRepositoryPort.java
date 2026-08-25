package com.cardbilling.billing.application.port;

import com.cardbilling.billing.domain.Customer;

public interface CustomerRepositoryPort {

    Customer save(Customer customer);

    long count();
}

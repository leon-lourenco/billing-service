package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.application.port.CustomerRepositoryPort;
import com.cardbilling.billing.domain.Customer;
import org.springframework.stereotype.Repository;

@Repository
class CustomerRepositoryAdapter implements CustomerRepositoryPort {

    private final CustomerJpaRepository customers;

    CustomerRepositoryAdapter(CustomerJpaRepository customers) {
        this.customers = customers;
    }

    @Override
    public Customer save(Customer customer) {
        return customers.save(CustomerEntity.fromDomain(customer)).toDomain();
    }

    @Override
    public long count() {
        return customers.count();
    }
}

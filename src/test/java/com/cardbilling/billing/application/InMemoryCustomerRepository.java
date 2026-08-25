package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.CustomerRepositoryPort;
import com.cardbilling.billing.domain.Customer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class InMemoryCustomerRepository implements CustomerRepositoryPort {

    private final List<Customer> customers = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Customer save(Customer customer) {
        Customer stored = customer.id() == null ? customer.withId(nextId.getAndIncrement()) : customer;
        customers.add(stored);
        return stored;
    }

    @Override
    public long count() {
        return customers.size();
    }

    List<Customer> all() {
        return List.copyOf(customers);
    }
}

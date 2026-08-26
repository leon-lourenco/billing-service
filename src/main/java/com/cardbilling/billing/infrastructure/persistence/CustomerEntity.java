package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.domain.Customer;
import com.cardbilling.billing.domain.DocumentNumber;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "document_number", nullable = false, unique = true, length = 11)
    private String documentNumber;

    @Column(nullable = false)
    private String email;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CustomerEntity() {
    }

    static CustomerEntity fromDomain(Customer customer) {
        CustomerEntity entity = new CustomerEntity();
        entity.id = customer.id();
        entity.fullName = customer.fullName();
        entity.documentNumber = customer.documentNumber().value();
        entity.email = customer.email();
        entity.phoneNumber = customer.phoneNumber();
        entity.createdAt = customer.createdAt();
        return entity;
    }

    Customer toDomain() {
        return Customer.reconstitute(id, fullName, DocumentNumber.of(documentNumber), email, phoneNumber, createdAt);
    }
}

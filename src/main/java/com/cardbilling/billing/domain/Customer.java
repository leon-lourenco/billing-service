package com.cardbilling.billing.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** A cardholder. Same shape as the legacy monolith's {@code Customer}, without the JPA mapping. */
public class Customer {

    private final Long id;
    private final String fullName;
    private final DocumentNumber documentNumber;
    private final String email;
    private final String phoneNumber;
    private final LocalDateTime createdAt;

    private Customer(Long id, String fullName, DocumentNumber documentNumber, String email,
            String phoneNumber, LocalDateTime createdAt) {
        this.id = id;
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.documentNumber = Objects.requireNonNull(documentNumber, "documentNumber");
        this.email = Objects.requireNonNull(email, "email");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Customer register(String fullName, DocumentNumber documentNumber, String email,
            String phoneNumber, LocalDateTime registeredAt) {
        return new Customer(null, fullName, documentNumber, email, phoneNumber, registeredAt);
    }

    public static Customer reconstitute(Long id, String fullName, DocumentNumber documentNumber,
            String email, String phoneNumber, LocalDateTime createdAt) {
        return new Customer(id, fullName, documentNumber, email, phoneNumber, createdAt);
    }

    public Customer withId(Long assignedId) {
        return new Customer(assignedId, fullName, documentNumber, email, phoneNumber, createdAt);
    }

    public Long id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public DocumentNumber documentNumber() {
        return documentNumber;
    }

    public String email() {
        return email;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}

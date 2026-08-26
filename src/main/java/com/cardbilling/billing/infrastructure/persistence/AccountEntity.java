package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.domain.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts", indexes = @Index(name = "idx_accounts_customer", columnList = "customer_id"))
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Account.Status status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    protected AccountEntity() {
    }

    static AccountEntity fromDomain(Account account) {
        AccountEntity entity = new AccountEntity();
        entity.id = account.id();
        entity.customerId = account.customerId();
        entity.accountNumber = account.accountNumber();
        entity.status = account.status();
        entity.openedAt = account.openedAt();
        return entity;
    }

    Account toDomain() {
        return Account.reconstitute(id, customerId, accountNumber, status, openedAt);
    }
}

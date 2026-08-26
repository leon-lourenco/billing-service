package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.domain.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * A payment row. The unique constraint on {@code external_reference} is the actual guarantee
 * behind "replaying a reconciliation match is a no-op" - the use case checks first, but two
 * concurrent replays would both pass that check, and this is what stops the second from
 * committing. Postgres treats NULLs as distinct, so internal payments with no reference are
 * unaffected by it.
 */
@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(
        name = "uk_payments_external_reference", columnNames = "external_reference"))
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private InvoiceEntity invoice;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Payment.Source source;

    /** The statement line this payment came from, when it came from one. */
    @Column(name = "external_reference")
    private String externalReference;

    protected PaymentEntity() {
    }

    static PaymentEntity createFrom(Payment payment, InvoiceEntity invoice) {
        PaymentEntity entity = new PaymentEntity();
        entity.invoice = invoice;
        entity.amountCents = payment.amount().cents();
        entity.paidAt = payment.paidAt();
        entity.source = payment.source();
        entity.externalReference = payment.externalReference().orElse(null);
        return entity;
    }

    Payment toDomain() {
        return Payment.reconstitute(id, Money.ofCents(amountCents), paidAt, source, externalReference);
    }
}

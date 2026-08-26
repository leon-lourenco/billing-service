package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.domain.InterestAccrual;
import com.cardbilling.billing.domain.InterestCharge;
import com.cardbilling.billing.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * One day's interest on one invoice. The unique constraint on
 * {@code (invoice_id, accrual_date)} is what makes the idempotency claim in the API contract
 * true rather than merely intended: the legacy relied on each caller checking a date field
 * first, and this makes a second charge for the same day impossible to commit at all.
 */
@Entity
@Table(name = "interest_accruals", uniqueConstraints = @UniqueConstraint(
        name = "uk_interest_accruals_invoice_date", columnNames = {"invoice_id", "accrual_date"}))
public class InterestAccrualEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private InvoiceEntity invoice;

    @Column(name = "accrual_date", nullable = false)
    private LocalDate accrualDate;

    @Column(name = "late_fee_cents", nullable = false)
    private long lateFeeCents;

    @Column(name = "daily_interest_cents", nullable = false)
    private long dailyInterestCents;

    protected InterestAccrualEntity() {
    }

    static InterestAccrualEntity createFrom(InterestAccrual accrual, InvoiceEntity invoice) {
        InterestAccrualEntity entity = new InterestAccrualEntity();
        entity.invoice = invoice;
        entity.accrualDate = accrual.accrualDate();
        entity.lateFeeCents = accrual.charge().lateFee().cents();
        entity.dailyInterestCents = accrual.charge().dailyInterest().cents();
        return entity;
    }

    InterestAccrual toDomain() {
        return InterestAccrual.reconstitute(id, accrualDate,
                InterestCharge.of(Money.ofCents(lateFeeCents), Money.ofCents(dailyInterestCents)));
    }
}

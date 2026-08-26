package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.domain.Cardholder;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.InterestAccrual;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.domain.Payment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The invoice row, and the two indexes this service exists to make possible.
 *
 * <p>{@code idx_invoices_search} covers the reconciliation lookup - equality on cardholder
 * document and amount owed, then a small range scan on due date. That composite only works
 * because the cardholder's document number is denormalised onto this row: the alternative is a
 * four-table join (invoice - card - account - customer) per statement line, which cannot be served
 * from one index and is most of what made the legacy's matching a scan. The value is copied in at
 * closing time and never changes afterwards, so there is nothing to keep in sync.
 *
 * <p>{@code amount_owed_cents} is likewise stored rather than computed at query time, for the
 * same reason: {@code total + interest} in a WHERE clause cannot use an index. It is maintained
 * on every write from the aggregate, which is the only thing that ever mutates either component.
 */
@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoices_search", columnList = "customer_document_number, amount_owed_cents, due_date"),
        @Index(name = "idx_invoices_overdue", columnList = "status, due_date"),
        @Index(name = "idx_invoices_card", columnList = "card_id")
})
public class InvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    /**
     * Denormalised alongside the document number, and for the same reason: a caller that reads
     * overdue invoices here and then acts on the cardholder elsewhere - notifying them, say -
     * needs their id without a second round trip per invoice.
     */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_document_number", nullable = false, length = 11)
    private String customerDocumentNumber;

    /** The billing cycle this invoice covers, e.g. "2026-08". */
    @Column(name = "reference_month", nullable = false)
    private String referenceMonth;

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "total_amount_cents", nullable = false)
    private long totalAmountCents;

    @Column(name = "interest_applied_cents", nullable = false)
    private long interestAppliedCents;

    /** Denormalised {@code total + interest}, so the reconciliation index can cover the amount. */
    @Column(name = "amount_owed_cents", nullable = false)
    private long amountOwedCents;

    @Column(name = "last_interest_accrual_date")
    private LocalDate lastInterestAccrualDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Invoice.Status status;

    // Sets rather than lists on purpose: both are fetched together by the entity graph below, and
    // Hibernate refuses to fetch two bags (unindexed lists) in one query. They are ordered back
    // into a stable sequence in toDomain() instead, so the domain still sees them oldest-first.
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PaymentEntity> payments = new LinkedHashSet<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<InterestAccrualEntity> interestAccruals = new LinkedHashSet<>();

    protected InvoiceEntity() {
    }

    static InvoiceEntity createFrom(Invoice invoice) {
        InvoiceEntity entity = new InvoiceEntity();
        entity.id = invoice.id();
        entity.cardId = invoice.cardId();
        entity.customerId = invoice.customerId();
        entity.customerDocumentNumber = invoice.customerDocumentNumber().value();
        entity.referenceMonth = invoice.referenceMonth();
        entity.closingDate = invoice.closingDate();
        entity.dueDate = invoice.dueDate();
        entity.totalAmountCents = invoice.totalAmount().cents();
        entity.syncMutableStateFrom(invoice);
        return entity;
    }

    /**
     * Copies back whatever the aggregate changed. Payments and accruals are append-only, so the
     * ones already carrying an id are untouched and only the new ones are added.
     */
    void syncMutableStateFrom(Invoice invoice) {
        this.interestAppliedCents = invoice.interestApplied().cents();
        this.amountOwedCents = invoice.amountOwed().cents();
        this.lastInterestAccrualDate = invoice.lastInterestAccrualDate().orElse(null);
        this.status = invoice.status();

        for (Payment payment : invoice.payments()) {
            if (payment.id() == null) {
                payments.add(PaymentEntity.createFrom(payment, this));
            }
        }
        for (InterestAccrual accrual : invoice.interestAccruals()) {
            if (accrual.id() == null) {
                interestAccruals.add(InterestAccrualEntity.createFrom(accrual, this));
            }
        }
    }

    Invoice toDomain() {
        return Invoice.reconstitute(
                id,
                cardId,
                Cardholder.of(customerId, DocumentNumber.of(customerDocumentNumber)),
                referenceMonth,
                closingDate,
                dueDate,
                Money.ofCents(totalAmountCents),
                Money.ofCents(interestAppliedCents),
                lastInterestAccrualDate,
                status,
                payments.stream()
                        .map(PaymentEntity::toDomain)
                        .sorted(Comparator.comparing(Payment::paidAt))
                        .toList(),
                interestAccruals.stream()
                        .map(InterestAccrualEntity::toDomain)
                        .sorted(Comparator.comparing(InterestAccrual::accrualDate))
                        .toList());
    }

    Long getId() {
        return id;
    }
}

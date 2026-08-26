package com.cardbilling.billing.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The aggregate root of this service: a closed billing cycle, everything charged to it since, and
 * everything paid against it.
 *
 * <p>Interest and payments only ever happen through this type. That matters because both carry a
 * rule that is easy to get wrong from the outside - interest must not be charged twice for the
 * same day, and a payment must not be recorded twice for the same bank statement line - and the
 * legacy monolith enforced both by trusting each caller to check first. Here the aggregate
 * refuses, so a caller that forgets gets an exception instead of a double charge.
 */
public class Invoice {

    public enum Status {
        OPEN, CLOSED, PAID, OVERDUE
    }

    private final Long id;
    private final long cardId;
    /**
     * Denormalised at closing time. The document number is what {@code /invoices/search} indexes
     * on, and the customer id is what a caller needs to act on the cardholder rather than just
     * identify the invoice - see {@code InvoiceEntity} for why both live on the invoice row
     * rather than being joined for on every lookup.
     */
    private final Cardholder cardholder;
    private final String referenceMonth;
    private final LocalDate closingDate;
    private final LocalDate dueDate;
    private final Money totalAmount;
    private Money interestApplied;
    private LocalDate lastInterestAccrualDate;
    private Status status;
    private final List<Payment> payments;
    private final List<InterestAccrual> interestAccruals;

    private Invoice(Long id, long cardId, Cardholder cardholder, String referenceMonth,
            LocalDate closingDate, LocalDate dueDate, Money totalAmount, Money interestApplied,
            LocalDate lastInterestAccrualDate, Status status, List<Payment> payments,
            List<InterestAccrual> interestAccruals) {
        this.id = id;
        this.cardId = cardId;
        this.cardholder = Objects.requireNonNull(cardholder, "cardholder");
        this.referenceMonth = Objects.requireNonNull(referenceMonth, "referenceMonth");
        this.closingDate = Objects.requireNonNull(closingDate, "closingDate");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount");
        this.interestApplied = Objects.requireNonNull(interestApplied, "interestApplied");
        this.lastInterestAccrualDate = lastInterestAccrualDate;
        this.status = Objects.requireNonNull(status, "status");
        this.payments = new ArrayList<>(payments);
        this.interestAccruals = new ArrayList<>(interestAccruals);
    }

    /** Closes a cycle into a new invoice. The only way an invoice is ever created. */
    public static Invoice close(long cardId, Cardholder cardholder, BillingCycle cycle, Money totalAmount) {
        return new Invoice(null, cardId, cardholder, cycle.referenceMonth(), cycle.closingDate(),
                cycle.dueDate(), totalAmount, Money.ZERO, null, Status.CLOSED, List.of(), List.of());
    }

    /** Rebuilds an invoice from storage. For the persistence adapter only. */
    public static Invoice reconstitute(Long id, long cardId, Cardholder cardholder,
            String referenceMonth, LocalDate closingDate, LocalDate dueDate, Money totalAmount,
            Money interestApplied, LocalDate lastInterestAccrualDate, Status status, List<Payment> payments,
            List<InterestAccrual> interestAccruals) {
        return new Invoice(id, cardId, cardholder, referenceMonth, closingDate, dueDate, totalAmount,
                interestApplied, lastInterestAccrualDate, status, payments, interestAccruals);
    }

    public Invoice withId(Long assignedId) {
        return new Invoice(assignedId, cardId, cardholder, referenceMonth, closingDate, dueDate,
                totalAmount, interestApplied, lastInterestAccrualDate, status, payments, interestAccruals);
    }

    /**
     * Charges one day's interest.
     *
     * @throws InterestAlreadyAccruedException if this invoice has already been charged for that
     *         day - the caller retrying after a partial failure is expected, double-charging is not
     */
    public InterestAccrual accrueInterest(InterestCharge charge, LocalDate accrualDate) {
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(accrualDate, "accrualDate");
        if (hasAccruedInterestOn(accrualDate)) {
            throw new InterestAlreadyAccruedException(id, accrualDate);
        }

        InterestAccrual accrual = InterestAccrual.on(accrualDate, charge);
        interestAccruals.add(accrual);
        interestApplied = interestApplied.plus(charge.total());
        if (lastInterestAccrualDate == null || accrualDate.isAfter(lastInterestAccrualDate)) {
            lastInterestAccrualDate = accrualDate;
        }
        if (status != Status.PAID) {
            status = Status.OVERDUE;
        }
        return accrual;
    }

    /**
     * Records money received, and marks the invoice paid once the full amount owed is covered.
     *
     * @throws DuplicatePaymentException if a payment carrying the same external reference is
     *         already on this invoice - replaying a reconciliation match must be a no-op
     */
    public Payment recordPayment(Money amount, LocalDateTime paidAt, Payment.Source source,
            String externalReference) {
        if (externalReference != null && !externalReference.isBlank() && hasPaymentWith(externalReference)) {
            throw new DuplicatePaymentException(externalReference);
        }

        Payment payment = Payment.of(amount, paidAt, source, externalReference);
        payments.add(payment);
        if (amountPaid().isAtLeast(amountOwed())) {
            status = Status.PAID;
        }
        return payment;
    }

    /** What the cardholder owes in total: the closed cycle plus every interest charge since. */
    public Money amountOwed() {
        return totalAmount.plus(interestApplied);
    }

    public Money amountPaid() {
        return payments.stream().map(Payment::amount).reduce(Money.ZERO, Money::plus);
    }

    /** What is still outstanding. Zero or negative once the invoice is covered. */
    public Money amountDue() {
        return amountOwed().minus(amountPaid());
    }

    public boolean isPaid() {
        return status == Status.PAID;
    }

    public boolean isOverdueAsOf(LocalDate asOf) {
        return !isPaid() && asOf.isAfter(dueDate);
    }

    public boolean hasAccruedInterestOn(LocalDate accrualDate) {
        return interestAccruals.stream().anyMatch(accrual -> accrual.accrualDate().equals(accrualDate));
    }

    /** Whether the one-off late fee has already been charged on this invoice. */
    public boolean hasEverAccruedInterest() {
        return !interestAccruals.isEmpty();
    }

    public boolean hasPaymentWith(String externalReference) {
        return payments.stream()
                .anyMatch(payment -> payment.externalReference()
                        .filter(reference -> reference.equals(externalReference))
                        .isPresent());
    }

    public Long id() {
        return id;
    }

    public long cardId() {
        return cardId;
    }

    public Cardholder cardholder() {
        return cardholder;
    }

    /** Who this invoice is owed by. Callers acting on the cardholder address them by this id. */
    public long customerId() {
        return cardholder.customerId();
    }

    public DocumentNumber customerDocumentNumber() {
        return cardholder.documentNumber();
    }

    public String referenceMonth() {
        return referenceMonth;
    }

    public LocalDate closingDate() {
        return closingDate;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public Money totalAmount() {
        return totalAmount;
    }

    public Money interestApplied() {
        return interestApplied;
    }

    public Optional<LocalDate> lastInterestAccrualDate() {
        return Optional.ofNullable(lastInterestAccrualDate);
    }

    public Status status() {
        return status;
    }

    public List<Payment> payments() {
        return Collections.unmodifiableList(payments);
    }

    public List<InterestAccrual> interestAccruals() {
        return Collections.unmodifiableList(interestAccruals);
    }
}

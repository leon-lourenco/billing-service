package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.application.InvoiceSearchQuery;
import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.Invoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Maps the {@link Invoice} aggregate to and from its rows. */
@Repository
class InvoiceRepositoryAdapter implements InvoiceRepositoryPort {

    private final InvoiceJpaRepository invoices;
    private final PaymentJpaRepository payments;

    InvoiceRepositoryAdapter(InvoiceJpaRepository invoices, PaymentJpaRepository payments) {
        this.invoices = invoices;
        this.payments = payments;
    }

    @Override
    public Invoice save(Invoice invoice) {
        if (invoice.id() == null) {
            return invoices.save(InvoiceEntity.createFrom(invoice)).toDomain();
        }

        // An existing aggregate is re-read and updated in place rather than replaced, so the
        // payments and accruals already persisted keep their identity and only the new ones are
        // inserted.
        InvoiceEntity entity = invoices.findWithDetailsById(invoice.id())
                .orElseGet(() -> InvoiceEntity.createFrom(invoice));
        entity.syncMutableStateFrom(invoice);
        return invoices.save(entity).toDomain();
    }

    @Override
    public Optional<Invoice> findById(long invoiceId) {
        return invoices.findWithDetailsById(invoiceId).map(InvoiceEntity::toDomain);
    }

    @Override
    public List<Invoice> findOverdueAsOf(LocalDate asOf) {
        return invoices.findByStatusNotAndDueDateBefore(Invoice.Status.PAID, asOf).stream()
                .map(InvoiceEntity::toDomain)
                .toList();
    }

    @Override
    public List<Invoice> search(InvoiceSearchQuery query) {
        List<InvoiceEntity> matches = query.amountOwed() == null
                ? invoices.searchByDocumentInWindow(
                        query.documentNumber().value(),
                        query.earliestDueDate(),
                        query.latestDueDate(),
                        Invoice.Status.PAID)
                : invoices.search(
                        query.documentNumber().value(),
                        query.amountOwed().cents(),
                        query.earliestDueDate(),
                        query.latestDueDate(),
                        Invoice.Status.PAID);
        return matches.stream().map(InvoiceEntity::toDomain).toList();
    }

    @Override
    public boolean existsPaymentWithExternalReference(String externalReference) {
        return payments.existsByExternalReference(externalReference);
    }
}

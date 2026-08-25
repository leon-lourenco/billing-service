package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.Payment;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A fake invoice store, so the use case tests exercise the rules rather than a database. The
 * query methods deliberately reimplement the same filters the JPA adapter expresses in SQL - the
 * integration tests are what prove those two agree.
 */
class InMemoryInvoiceRepository implements InvoiceRepositoryPort {

    private final Map<Long, Invoice> invoices = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Invoice save(Invoice invoice) {
        Invoice stored = invoice.id() == null ? invoice.withId(nextId.getAndIncrement()) : invoice;
        invoices.put(stored.id(), stored);
        return stored;
    }

    @Override
    public Optional<Invoice> findById(long invoiceId) {
        return Optional.ofNullable(invoices.get(invoiceId));
    }

    @Override
    public List<Invoice> findOverdueAsOf(LocalDate asOf) {
        return invoices.values().stream()
                .filter(invoice -> invoice.isOverdueAsOf(asOf))
                .toList();
    }

    @Override
    public List<Invoice> search(InvoiceSearchQuery query) {
        return invoices.values().stream()
                .filter(invoice -> !invoice.isPaid())
                .filter(invoice -> invoice.customerDocumentNumber().equals(query.documentNumber()))
                .filter(invoice -> invoice.amountOwed().equals(query.amountOwed()))
                .filter(invoice -> !invoice.dueDate().isBefore(query.earliestDueDate()))
                .filter(invoice -> !invoice.dueDate().isAfter(query.latestDueDate()))
                .toList();
    }

    @Override
    public boolean existsPaymentWithExternalReference(String externalReference) {
        return invoices.values().stream()
                .flatMap(invoice -> invoice.payments().stream())
                .map(Payment::externalReference)
                .anyMatch(reference -> reference.filter(externalReference::equals).isPresent());
    }

    List<Invoice> all() {
        return new ArrayList<>(invoices.values());
    }
}

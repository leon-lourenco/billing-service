package com.cardbilling.billing.application.port;

import com.cardbilling.billing.application.InvoiceSearchQuery;
import com.cardbilling.billing.domain.Invoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Storage for the {@link Invoice} aggregate. Implemented in {@code infrastructure.persistence};
 * the application layer only ever sees this interface.
 */
public interface InvoiceRepositoryPort {

    /** Persists the aggregate and everything it owns, returning it with any assigned ids. */
    Invoice save(Invoice invoice);

    Optional<Invoice> findById(long invoiceId);

    /** Every unpaid invoice whose due date has passed as of the given date. */
    List<Invoice> findOverdueAsOf(LocalDate asOf);

    /**
     * The indexed lookup that replaces the legacy's nested loop: unpaid invoices for this
     * cardholder owing this exact amount, falling due within the query's tolerance window.
     */
    List<Invoice> search(InvoiceSearchQuery query);

    /**
     * Whether any invoice already carries a payment for this statement line. Checked across all
     * invoices, since a replayed reconciliation run may well aim the retry at a different invoice
     * than the one it originally matched.
     */
    boolean existsPaymentWithExternalReference(String externalReference);
}

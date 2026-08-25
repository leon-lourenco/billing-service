package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.Invoice;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "which unpaid invoice does this bank statement line belong to?" with an indexed
 * lookup.
 *
 * <p>This endpoint exists because the legacy had no way to ask that question of the database:
 * an external statement and this system never shared an identifier, so matching meant loading
 * every open invoice and scanning them per line - O(lines x invoices). Asking it as a query
 * against an index over cardholder document, amount owed and due date turns the same job into
 * one indexed lookup per line, and {@code reconciliation-service} never has to hold more than a
 * single line and its result in memory.
 */
@Service
public class SearchInvoicesUseCase {

    private final InvoiceRepositoryPort invoiceRepository;

    public SearchInvoicesUseCase(InvoiceRepositoryPort invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public List<Invoice> search(InvoiceSearchQuery query) {
        return invoiceRepository.search(query);
    }
}

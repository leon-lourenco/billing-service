package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.Invoice;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every unpaid invoice past its due date as of a given day - the working set
 * {@code collections-service} reads to decide what to charge and who to chase.
 */
@Service
public class GetOverdueInvoicesUseCase {

    private final InvoiceRepositoryPort invoiceRepository;

    public GetOverdueInvoicesUseCase(InvoiceRepositoryPort invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public List<Invoice> overdueAsOf(LocalDate asOf) {
        return invoiceRepository.findOverdueAsOf(asOf);
    }
}

package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.InvoiceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records money received against an invoice, marking it paid once the full amount owed is
 * covered.
 *
 * <p>A payment carrying an external reference already on file is a replayed reconciliation match:
 * the invoice comes back untouched rather than gaining a second payment. As with interest, the
 * check here is the fast path and the unique constraint on the payment row is what actually
 * guarantees it under concurrency.
 */
@Service
public class RecordPaymentUseCase {

    private final InvoiceRepositoryPort invoiceRepository;

    public RecordPaymentUseCase(InvoiceRepositoryPort invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public PaymentRecordingResult record(RecordPaymentCommand command) {
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));

        if (command.hasExternalReference()
                && invoiceRepository.existsPaymentWithExternalReference(command.externalReference())) {
            return PaymentRecordingResult.alreadyRecorded(invoice);
        }

        invoice.recordPayment(command.amount(), command.paidAt(), command.source(), command.externalReference());
        return PaymentRecordingResult.recorded(invoiceRepository.save(invoice));
    }
}

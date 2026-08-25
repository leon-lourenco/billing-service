package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.InterestCharge;
import com.cardbilling.billing.domain.InterestPolicy;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.InvoiceNotFoundException;
import com.cardbilling.billing.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charges one day's interest on an invoice, at most once per day.
 *
 * <p>A repeat request for a day already charged returns the invoice unchanged rather than
 * failing: an accrual run that died halfway through is meant to be rerun, and the invoices it
 * already touched should quietly stay as they are. Two requests racing for the same day still
 * get caught, by the unique constraint on the accrual row - see {@code InterestAccrualEntity}.
 */
@Service
public class ApplyInterestUseCase {

    private final InvoiceRepositoryPort invoiceRepository;
    private final InterestPolicy interestPolicy;

    public ApplyInterestUseCase(InvoiceRepositoryPort invoiceRepository, InterestPolicy interestPolicy) {
        this.invoiceRepository = invoiceRepository;
        this.interestPolicy = interestPolicy;
    }

    @Transactional
    public InterestApplicationResult apply(ApplyInterestCommand command) {
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));

        if (invoice.hasAccruedInterestOn(command.accrualDate())) {
            return InterestApplicationResult.alreadyAccrued(invoice);
        }

        invoice.accrueInterest(chargeFor(command, invoice), command.accrualDate());
        return InterestApplicationResult.applied(invoiceRepository.save(invoice));
    }

    private InterestCharge chargeFor(ApplyInterestCommand command, Invoice invoice) {
        if (!command.specifiesAmounts()) {
            return interestPolicy.chargeFor(invoice);
        }
        return InterestCharge.of(
                command.optionalLateFee().orElse(Money.ZERO),
                command.optionalDailyInterest().orElse(Money.ZERO));
    }
}

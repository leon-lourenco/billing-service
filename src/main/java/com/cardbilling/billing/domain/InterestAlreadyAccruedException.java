package com.cardbilling.billing.domain;

import java.time.LocalDate;

/**
 * This invoice has already been charged interest for that day.
 *
 * <p>Like a duplicate payment, this is a replay rather than a mistake: an accrual run that failed
 * partway through gets rerun, and the invoices it already touched must not be charged twice.
 */
public class InterestAlreadyAccruedException extends BillingDomainException {

    private final Long invoiceId;
    private final LocalDate accrualDate;

    public InterestAlreadyAccruedException(Long invoiceId, LocalDate accrualDate) {
        super("Invoice " + invoiceId + " has already accrued interest for " + accrualDate);
        this.invoiceId = invoiceId;
        this.accrualDate = accrualDate;
    }

    public Long invoiceId() {
        return invoiceId;
    }

    public LocalDate accrualDate() {
        return accrualDate;
    }
}

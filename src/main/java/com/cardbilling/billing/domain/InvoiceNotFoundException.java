package com.cardbilling.billing.domain;

/** No invoice exists with the requested id. */
public class InvoiceNotFoundException extends BillingDomainException {

    private final long invoiceId;

    public InvoiceNotFoundException(long invoiceId) {
        super("No invoice found with id " + invoiceId);
        this.invoiceId = invoiceId;
    }

    public long invoiceId() {
        return invoiceId;
    }
}

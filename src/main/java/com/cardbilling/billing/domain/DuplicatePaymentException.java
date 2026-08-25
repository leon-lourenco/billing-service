package com.cardbilling.billing.domain;

/**
 * A payment carrying this external reference has already been recorded.
 *
 * <p>Raised when a reconciliation match is replayed. The caller is not doing anything wrong -
 * retrying a run that failed halfway is the correct thing to do - so the web layer answers this
 * with the payment that already exists rather than treating it as a failure.
 */
public class DuplicatePaymentException extends BillingDomainException {

    private final String externalReference;

    public DuplicatePaymentException(String externalReference) {
        super("A payment with external reference '" + externalReference + "' has already been recorded");
        this.externalReference = externalReference;
    }

    public String externalReference() {
        return externalReference;
    }
}

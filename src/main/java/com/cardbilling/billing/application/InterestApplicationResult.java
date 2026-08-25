package com.cardbilling.billing.application;

import com.cardbilling.billing.domain.Invoice;

/**
 * The outcome of an interest request. {@code applied} is false when the invoice had already been
 * charged for that day - the request was a replay, nothing changed, and the invoice returned is
 * the state that was already there.
 */
public record InterestApplicationResult(Invoice invoice, boolean applied) {

    public static InterestApplicationResult applied(Invoice invoice) {
        return new InterestApplicationResult(invoice, true);
    }

    public static InterestApplicationResult alreadyAccrued(Invoice invoice) {
        return new InterestApplicationResult(invoice, false);
    }
}

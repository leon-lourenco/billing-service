package com.cardbilling.billing.application;

import com.cardbilling.billing.domain.Invoice;

/**
 * The outcome of a payment request. {@code recorded} is false when a payment for the same
 * statement line was already on file - the reconciliation run was replayed, and no second
 * payment was created.
 */
public record PaymentRecordingResult(Invoice invoice, boolean recorded) {

    public static PaymentRecordingResult recorded(Invoice invoice) {
        return new PaymentRecordingResult(invoice, true);
    }

    public static PaymentRecordingResult alreadyRecorded(Invoice invoice) {
        return new PaymentRecordingResult(invoice, false);
    }
}

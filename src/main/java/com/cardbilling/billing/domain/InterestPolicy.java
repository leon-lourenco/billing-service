package com.cardbilling.billing.domain;

/**
 * What an overdue invoice costs per day: a flat 2% late fee, charged once the first time the
 * invoice goes overdue, plus 1% simple daily interest every day after that - the "multa + mora
 * diária" a Brazilian card issuer typically charges, and the same rule the legacy monolith's
 * interest job applied.
 *
 * <p>Simple, not compounding: both components are computed against the invoice's original closed
 * total, never against interest already applied. Charging interest on interest is a different
 * product with different disclosure requirements, and the legacy deliberately did not do it.
 *
 * <p>This rule lives in {@code billing-service} rather than in the caller that triggers accrual,
 * so that "what interest costs" has exactly one answer no matter who asks for it to be applied.
 */
public final class InterestPolicy {

    private static final double LATE_FEE_RATE = 0.02;
    private static final double DAILY_INTEREST_RATE = 0.01;

    public InterestCharge chargeFor(Invoice invoice) {
        Money lateFee = invoice.hasEverAccruedInterest()
                ? Money.ZERO
                : invoice.totalAmount().percentage(LATE_FEE_RATE);
        Money dailyInterest = invoice.totalAmount().percentage(DAILY_INTEREST_RATE);
        return InterestCharge.of(lateFee, dailyInterest);
    }
}

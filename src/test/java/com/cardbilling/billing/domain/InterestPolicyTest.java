package com.cardbilling.billing.domain;

import static com.cardbilling.billing.domain.InvoiceFixtures.DUE_DATE;
import static com.cardbilling.billing.domain.InvoiceFixtures.closedInvoiceOf;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterestPolicyTest {

    private final InterestPolicy policy = new InterestPolicy();

    @Test
    @DisplayName("charges the 2% late fee plus 1% daily interest the first time an invoice goes overdue")
    void firstAccrualChargesFeeAndDailyInterest() {
        InterestCharge charge = policy.chargeFor(closedInvoiceOf(100_000));

        assertThat(charge.lateFee()).isEqualTo(Money.ofCents(2_000));
        assertThat(charge.dailyInterest()).isEqualTo(Money.ofCents(1_000));
        assertThat(charge.total()).isEqualTo(Money.ofCents(3_000));
    }

    @Test
    @DisplayName("charges only daily interest on every day after the first")
    void laterAccrualsChargeDailyInterestOnly() {
        Invoice invoice = closedInvoiceOf(100_000);
        invoice.accrueInterest(policy.chargeFor(invoice), DUE_DATE.plusDays(1));

        InterestCharge secondDay = policy.chargeFor(invoice);

        assertThat(secondDay.lateFee()).isEqualTo(Money.ZERO);
        assertThat(secondDay.dailyInterest()).isEqualTo(Money.ofCents(1_000));
    }

    @Test
    @DisplayName("computes daily interest on the original total, never on interest already applied")
    void interestIsSimpleNotCompounding() {
        Invoice invoice = closedInvoiceOf(100_000);
        for (int day = 1; day <= 10; day++) {
            invoice.accrueInterest(policy.chargeFor(invoice), DUE_DATE.plusDays(day));
        }

        // Ten days of interest is still 1% of the original 100000 each day, not of the growing balance.
        assertThat(policy.chargeFor(invoice).dailyInterest()).isEqualTo(Money.ofCents(1_000));
        // 2000 fee + 10 x 1000 daily.
        assertThat(invoice.interestApplied()).isEqualTo(Money.ofCents(12_000));
    }

    @Test
    @DisplayName("rounds a fractional cent half-up, matching the legacy's arithmetic exactly")
    void roundsHalfUp() {
        // 2% of 12345 is 246.9 -> 247; 1% is 123.45 -> 123.
        InterestCharge charge = policy.chargeFor(closedInvoiceOf(12_345));

        assertThat(charge.lateFee()).isEqualTo(Money.ofCents(247));
        assertThat(charge.dailyInterest()).isEqualTo(Money.ofCents(123));
    }
}

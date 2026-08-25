package com.cardbilling.billing.domain;

import java.time.LocalDate;

/** Shared invoice fixtures, so each test names only the detail it actually cares about. */
final class InvoiceFixtures {

    static final DocumentNumber DOCUMENT = DocumentNumber.of("10000000042");
    static final LocalDate CLOSING_DATE = LocalDate.of(2026, 3, 15);
    static final LocalDate DUE_DATE = CLOSING_DATE.plusDays(BillingCycle.DAYS_UNTIL_DUE);

    private InvoiceFixtures() {
    }

    /** A freshly closed, unpaid invoice with an id already assigned, as it would come from storage. */
    static Invoice closedInvoiceOf(long totalCents) {
        return Invoice.close(1L, DOCUMENT, BillingCycle.closingOn(CLOSING_DATE), Money.ofCents(totalCents))
                .withId(100L);
    }
}

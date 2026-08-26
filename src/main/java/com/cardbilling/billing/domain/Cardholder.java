package com.cardbilling.billing.domain;

import java.util.Objects;

/**
 * Who an invoice belongs to: the customer's own id, and the document number an external bank
 * statement knows them by.
 *
 * <p>Both are stamped onto an invoice at closing time and never change afterwards. They travel
 * together because they answer the same question for two different audiences - the document
 * number is what {@code reconciliation-service} matches a statement line on, and the id is what
 * {@code notification-service} needs to address a notification to. Keeping them as one value
 * object also keeps them from being passed in the wrong order next to the card id, which two
 * adjacent longs invite.
 */
public record Cardholder(long customerId, DocumentNumber documentNumber) {

    public Cardholder {
        Objects.requireNonNull(documentNumber, "documentNumber");
    }

    public static Cardholder of(long customerId, DocumentNumber documentNumber) {
        return new Cardholder(customerId, documentNumber);
    }
}

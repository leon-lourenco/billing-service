package com.cardbilling.billing.domain;

/**
 * Input that cannot form a valid value - a document number that isn't one, a date window with a
 * negative width.
 *
 * <p>Distinct from a plain {@code IllegalArgumentException} on purpose. This one means "the
 * caller sent something we cannot make sense of", which is a 400; an
 * {@code IllegalArgumentException} escaping from somewhere else means this service has a bug,
 * which is a 500. Collapsing the two into one handler is how an internal fault ends up reported
 * as the caller's fault.
 */
public class MalformedValueException extends BillingDomainException {

    private final String field;

    public MalformedValueException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}

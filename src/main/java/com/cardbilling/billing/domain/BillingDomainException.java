package com.cardbilling.billing.domain;

/**
 * Base type for every rule this service enforces. Having one root means the web layer can map
 * domain failures to RFC 7807 responses exhaustively, and anything that escapes as a generic 500
 * is by definition a bug rather than a business outcome.
 */
public abstract class BillingDomainException extends RuntimeException {

    protected BillingDomainException(String message) {
        super(message);
    }
}

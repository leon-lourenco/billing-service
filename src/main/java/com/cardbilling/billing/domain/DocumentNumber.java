package com.cardbilling.billing.domain;

/**
 * A customer's document number (CPF-shaped: 11 digits).
 *
 * <p>This is the only identifier an external bank statement and this system ever agree on, which
 * makes it the join key {@code /invoices/search} indexes on. Validating the shape here means a
 * malformed statement line is rejected at the edge instead of quietly matching nothing and being
 * reported as "invoice not found" - two very different outcomes for whoever reads the
 * reconciliation report.
 */
public record DocumentNumber(String value) {

    private static final int LENGTH = 11;

    public DocumentNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Document number must not be blank");
        }
        if (value.length() != LENGTH || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                    "Document number must be exactly " + LENGTH + " digits, got: " + value);
        }
    }

    public static DocumentNumber of(String value) {
        return new DocumentNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

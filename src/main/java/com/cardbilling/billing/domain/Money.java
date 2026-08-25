package com.cardbilling.billing.domain;

/**
 * An amount of money in cents.
 *
 * <p>Currency is deliberately not modelled: this is a single-currency (BRL) card issuer, so a
 * currency field would be a constant carried through every arithmetic operation without ever
 * being read. What this type is actually for is stopping cents from being passed around as a
 * bare {@code long} - which is how a credit limit, an amount owed and a day count all end up
 * looking identical to the compiler. If a second currency ever shows up, it gets added here and
 * every call site that needs updating stops compiling, which is the point.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0L);

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(this.cents, other.cents));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(this.cents, other.cents));
    }

    /**
     * The given percentage of this amount, rounded half-up to the nearest cent - the same
     * rounding the legacy monolith's interest job used, kept identical so ported invoices accrue
     * the same amounts they always did.
     */
    public Money percentage(double rate) {
        return new Money(Math.round(this.cents * rate));
    }

    public boolean isPositive() {
        return this.cents > 0L;
    }

    public boolean isZeroOrLess() {
        return this.cents <= 0L;
    }

    public boolean isAtLeast(Money other) {
        return this.cents >= other.cents;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(this.cents, other.cents);
    }

    @Override
    public String toString() {
        return cents + " cents";
    }
}

package com.cardbilling.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("adds and subtracts in cents without drifting")
    void addsAndSubtracts() {
        Money total = Money.ofCents(10_00).plus(Money.ofCents(5_50));

        assertThat(total).isEqualTo(Money.ofCents(15_50));
        assertThat(total.minus(Money.ofCents(50))).isEqualTo(Money.ofCents(15_00));
    }

    @Test
    @DisplayName("subtraction can go negative - an overpaid invoice owes less than nothing")
    void subtractionMayGoNegative() {
        assertThat(Money.ofCents(100).minus(Money.ofCents(250))).isEqualTo(Money.ofCents(-150));
    }

    @Test
    @DisplayName("percentages round half-up to the nearest cent")
    void percentageRoundsHalfUp() {
        // 2% of 12345 cents is 246.9 cents
        assertThat(Money.ofCents(12_345).percentage(0.02)).isEqualTo(Money.ofCents(247));
        // 1% of 12345 cents is 123.45 cents
        assertThat(Money.ofCents(12_345).percentage(0.01)).isEqualTo(Money.ofCents(123));
        // exactly .5 rounds up
        assertThat(Money.ofCents(50).percentage(0.01)).isEqualTo(Money.ofCents(1));
    }

    @Test
    @DisplayName("overflow fails loudly rather than wrapping around into a credit")
    void overflowThrows() {
        Money huge = Money.ofCents(Long.MAX_VALUE);

        assertThatThrownBy(() -> huge.plus(Money.ofCents(1)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("compares by cents")
    void comparesByCents() {
        assertThat(Money.ofCents(500).isAtLeast(Money.ofCents(500))).isTrue();
        assertThat(Money.ofCents(499).isAtLeast(Money.ofCents(500))).isFalse();
        assertThat(Money.ofCents(1).isPositive()).isTrue();
        assertThat(Money.ZERO.isZeroOrLess()).isTrue();
        assertThat(Money.ofCents(-1).isZeroOrLess()).isTrue();
    }
}

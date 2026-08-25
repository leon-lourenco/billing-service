package com.cardbilling.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BillingCycleTest {

    @Test
    @DisplayName("falls due ten days after closing")
    void dueTenDaysAfterClosing() {
        BillingCycle cycle = BillingCycle.closingOn(LocalDate.of(2026, 3, 15));

        assertThat(cycle.dueDate()).isEqualTo(LocalDate.of(2026, 3, 25));
    }

    @Test
    @DisplayName("bills the month up to but not including closing day")
    void windowExcludesClosingDay() {
        BillingCycle cycle = BillingCycle.closingOn(LocalDate.of(2026, 3, 15));

        assertThat(cycle.windowStart()).isEqualTo(LocalDateTime.of(2026, 2, 15, 0, 0));
        assertThat(cycle.windowEnd()).isEqualTo(LocalDateTime.of(2026, 3, 15, 0, 0));
    }

    @Test
    @DisplayName("is filed under the closing month")
    void referenceMonthIsClosingMonth() {
        assertThat(BillingCycle.closingOn(LocalDate.of(2026, 3, 15)).referenceMonth()).isEqualTo("2026-03");
        assertThat(BillingCycle.closingOn(LocalDate.of(2026, 12, 1)).referenceMonth()).isEqualTo("2026-12");
    }

    @Test
    @DisplayName("closes only the cards whose cycle day is today")
    void closesOnlyMatchingCards() {
        BillingCycle cycle = BillingCycle.closingOn(LocalDate.of(2026, 3, 15));
        Card closingToday = Card.reconstitute(1L, 1L, "**** 0001", Money.ofCents(100_000), 15, Card.Status.ACTIVE);
        Card closingLater = Card.reconstitute(2L, 1L, "**** 0002", Money.ofCents(100_000), 20, Card.Status.ACTIVE);

        assertThat(cycle.closes(closingToday)).isTrue();
        assertThat(cycle.closes(closingLater)).isFalse();
    }

    @Test
    @DisplayName("a February closing still bills a full month back into January")
    void handlesShortMonths() {
        BillingCycle cycle = BillingCycle.closingOn(LocalDate.of(2026, 3, 30));

        assertThat(cycle.windowStart()).isEqualTo(LocalDateTime.of(2026, 2, 28, 0, 0));
    }
}

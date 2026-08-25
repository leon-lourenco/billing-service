package com.cardbilling.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.billing.domain.Card;
import com.cardbilling.billing.domain.CardTransaction;
import com.cardbilling.billing.domain.Customer;
import com.cardbilling.billing.domain.Money;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeedDemoDataUseCaseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 20, 9, 0);

    private record Fixture(InMemoryCustomerRepository customers, InMemoryCardRepository cards,
            InMemoryCardTransactionRepository transactions, SeedDemoDataUseCase useCase) {
    }

    private Fixture fixture() {
        InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        InMemoryCardRepository cards = new InMemoryCardRepository();
        InMemoryCardTransactionRepository transactions = new InMemoryCardTransactionRepository();
        return new Fixture(customers, cards, transactions,
                new SeedDemoDataUseCase(customers, accounts, cards, transactions));
    }

    @Test
    @DisplayName("seeds 150 customers, one card each, with four months of history")
    void seedsTheDemoDataset() {
        Fixture fixture = fixture();

        int transactionCount = fixture.useCase().seed(NOW);

        assertThat(fixture.customers().count()).isEqualTo(150);
        assertThat(fixture.cards().count()).isEqualTo(150);
        assertThat(transactionCount).isEqualTo(fixture.transactions().all().size());
        assertThat(transactionCount).isPositive();

        assertThat(fixture.transactions().all())
                .allSatisfy(transaction -> {
                    assertThat(transaction.transactionDate()).isAfterOrEqualTo(NOW.minusMonths(4));
                    assertThat(transaction.transactionDate()).isBefore(NOW);
                    assertThat(transaction.isBilled()).isFalse();
                });
    }

    @Test
    @DisplayName("the fixed seed makes the dataset identical across runs")
    void datasetIsReproducible() {
        Fixture first = fixture();
        Fixture second = fixture();

        int firstCount = first.useCase().seed(NOW);
        int secondCount = second.useCase().seed(NOW);

        assertThat(secondCount).isEqualTo(firstCount);
        assertThat(second.customers().all().stream().map(Customer::fullName).toList())
                .isEqualTo(first.customers().all().stream().map(Customer::fullName).toList());
        assertThat(cycleDays(second)).isEqualTo(cycleDays(first));
        assertThat(amounts(second)).isEqualTo(amounts(first));
    }

    @Test
    @DisplayName("document numbers are unique across the dataset")
    void documentNumbersAreUnique() {
        Fixture fixture = fixture();

        fixture.useCase().seed(NOW);

        assertThat(fixture.customers().all().stream().map(Customer::documentNumber).distinct().count())
                .isEqualTo(150);
    }

    @Test
    @DisplayName("cycle days land within 1-28 so every month has one")
    void cycleDaysAreAlwaysReachable() {
        Fixture fixture = fixture();

        fixture.useCase().seed(NOW);

        assertThat(cycleDays(fixture)).allMatch(day -> day >= 1 && day <= 28);
    }

    @Test
    @DisplayName("seeding an already-populated database does nothing")
    void doesNotReseed() {
        Fixture fixture = fixture();
        fixture.useCase().seed(NOW);
        int transactionsAfterFirstRun = fixture.transactions().all().size();

        int written = fixture.useCase().seed(NOW);

        assertThat(written).isZero();
        assertThat(fixture.customers().count()).isEqualTo(150);
        assertThat(fixture.transactions().all()).hasSize(transactionsAfterFirstRun);
    }

    private List<Integer> cycleDays(Fixture fixture) {
        return fixture.cards().all().stream().map(Card::billingCycleDay).toList();
    }

    private List<Long> amounts(Fixture fixture) {
        return fixture.transactions().all().stream()
                .map(CardTransaction::amount)
                .map(Money::cents)
                .toList();
    }
}

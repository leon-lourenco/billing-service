package com.cardbilling.billing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.billing.application.CloseInvoiceCycleUseCase;
import com.cardbilling.billing.application.CycleClosingSummary;
import com.cardbilling.billing.application.port.AccountRepositoryPort;
import com.cardbilling.billing.application.port.CardRepositoryPort;
import com.cardbilling.billing.application.port.CardTransactionRepositoryPort;
import com.cardbilling.billing.application.port.CustomerRepositoryPort;
import com.cardbilling.billing.domain.Account;
import com.cardbilling.billing.domain.Card;
import com.cardbilling.billing.domain.CardTransaction;
import com.cardbilling.billing.domain.Customer;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.infrastructure.PostgresIntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Cycle closing against a real database - specifically the card-to-cardholder join the use case
 * relies on, which an in-memory fake cannot vouch for.
 */
class CycleClosingIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 20, 9, 0);
    private static final AtomicLong UNIQUE = new AtomicLong(80);

    @Autowired
    private CustomerRepositoryPort customers;

    @Autowired
    private AccountRepositoryPort accounts;

    @Autowired
    private CardRepositoryPort cards;

    @Autowired
    private CardTransactionRepositoryPort transactions;

    @Autowired
    private CloseInvoiceCycleUseCase closeInvoiceCycle;

    private record Cardholder(Customer customer, Card card) {
    }

    private Cardholder cardholderWithCycleDay(int cycleDay) {
        long unique = UNIQUE.incrementAndGet();
        Customer customer = customers.save(Customer.register("Ana Ribeiro",
                DocumentNumber.of(String.format("%011d", 30_000_000_000L + unique)),
                "ana" + unique + "@example.com", "+5511900000000", NOW.minusMonths(6)));
        Account account = accounts.save(
                Account.open(customer.id(), "ACC-CLOSING-" + unique, NOW.minusMonths(6)));
        Card card = cards.save(Card.issue(account.id(), "**** **** **** 4321",
                Money.ofCents(500_000), cycleDay));
        return new Cardholder(customer, card);
    }

    @Test
    @DisplayName("closes a cycle, stamping the invoice with the cardholder found by joining through the account")
    void closesCycleWithCardholderDocument() {
        Cardholder cardholder = cardholderWithCycleDay(15);
        transactions.save(CardTransaction.record(cardholder.card().id(), "Padaria Pao Quente",
                Money.ofCents(12_000), LocalDateTime.of(2026, 3, 1, 12, 0)));
        transactions.save(CardTransaction.record(cardholder.card().id(), "Posto Ipiranga",
                Money.ofCents(8_000), LocalDateTime.of(2026, 3, 10, 12, 0)));

        CycleClosingSummary summary = closeInvoiceCycle.closeCyclesFor(LocalDate.of(2026, 3, 15));

        assertThat(summary.invoicesClosed()).isPositive();
        assertThat(transactions.findUnbilledForCardBetween(cardholder.card().id(),
                LocalDateTime.of(2026, 2, 15, 0, 0), LocalDateTime.of(2026, 3, 15, 0, 0))).isEmpty();
    }

    @Test
    @DisplayName("leaves a card whose cycle day is another date entirely alone")
    void ignoresCardsOnOtherCycleDays() {
        Cardholder cardholder = cardholderWithCycleDay(7);
        transactions.save(CardTransaction.record(cardholder.card().id(), "Livraria Cultura",
                Money.ofCents(5_000), LocalDateTime.of(2026, 3, 1, 12, 0)));

        closeInvoiceCycle.closeCyclesFor(LocalDate.of(2026, 3, 15));

        assertThat(transactions.findUnbilledForCardBetween(cardholder.card().id(),
                LocalDateTime.of(2026, 2, 7, 0, 0), LocalDateTime.of(2026, 3, 7, 0, 0))).hasSize(1);
    }

    @Test
    @DisplayName("closing the same date twice bills nothing a second time")
    void rerunningClosingBillsNothingTwice() {
        Cardholder cardholder = cardholderWithCycleDay(20);
        transactions.save(CardTransaction.record(cardholder.card().id(), "Farmacia Popular",
                Money.ofCents(9_000), LocalDateTime.of(2026, 3, 5, 12, 0)));

        CycleClosingSummary first = closeInvoiceCycle.closeCyclesFor(LocalDate.of(2026, 3, 20));
        CycleClosingSummary second = closeInvoiceCycle.closeCyclesFor(LocalDate.of(2026, 3, 20));

        assertThat(first.invoicesClosed()).isPositive();
        assertThat(second.invoicesClosed()).isZero();
    }
}

package com.cardbilling.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.billing.domain.Card;
import com.cardbilling.billing.domain.CardTransaction;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.Money;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CloseInvoiceCycleUseCaseTest {

    private static final DocumentNumber CARDHOLDER = DocumentNumber.of("10000000042");
    private static final LocalDate CLOSING_DATE = LocalDate.of(2026, 3, 15);

    private InMemoryCardRepository cards;
    private InMemoryCardTransactionRepository transactions;
    private InMemoryInvoiceRepository invoices;
    private CloseInvoiceCycleUseCase useCase;

    @BeforeEach
    void setUp() {
        cards = new InMemoryCardRepository();
        transactions = new InMemoryCardTransactionRepository();
        invoices = new InMemoryInvoiceRepository();
        useCase = new CloseInvoiceCycleUseCase(cards, transactions, invoices);
    }

    private Card activeCardClosingOn(int cycleDay) {
        return cards.saveHeldBy(Card.issue(1L, "**** **** **** 1000", Money.ofCents(500_000), cycleDay), CARDHOLDER);
    }

    private void transactionOn(Card card, LocalDateTime when, long cents) {
        transactions.save(CardTransaction.record(card.id(), "Padaria Pao Quente", Money.ofCents(cents), when));
    }

    @Test
    @DisplayName("sums the cycle's transactions into one invoice due ten days after closing")
    void closesCycleIntoInvoice() {
        Card card = activeCardClosingOn(15);
        transactionOn(card, LocalDateTime.of(2026, 2, 20, 12, 0), 10_000);
        transactionOn(card, LocalDateTime.of(2026, 3, 1, 12, 0), 25_000);

        CycleClosingSummary summary = useCase.closeCyclesFor(CLOSING_DATE);

        assertThat(summary.invoicesClosed()).isEqualTo(1);
        assertThat(summary.totalBilled()).isEqualTo(Money.ofCents(35_000));

        Invoice invoice = invoices.all().getFirst();
        assertThat(invoice.totalAmount()).isEqualTo(Money.ofCents(35_000));
        assertThat(invoice.dueDate()).isEqualTo(LocalDate.of(2026, 3, 25));
        assertThat(invoice.referenceMonth()).isEqualTo("2026-03");
        assertThat(invoice.status()).isEqualTo(Invoice.Status.CLOSED);
        assertThat(invoice.customerDocumentNumber()).isEqualTo(CARDHOLDER);
    }

    @Test
    @DisplayName("assigns every billed transaction to the invoice it closed into")
    void assignsTransactionsToInvoice() {
        Card card = activeCardClosingOn(15);
        transactionOn(card, LocalDateTime.of(2026, 2, 20, 12, 0), 10_000);
        transactionOn(card, LocalDateTime.of(2026, 3, 1, 12, 0), 25_000);

        useCase.closeCyclesFor(CLOSING_DATE);

        Long invoiceId = invoices.all().getFirst().id();
        assertThat(transactions.all())
                .allSatisfy(transaction -> assertThat(transaction.invoiceId()).contains(invoiceId));
    }

    @Test
    @DisplayName("skips a card whose cycle day is not today")
    void skipsCardsClosingOnAnotherDay() {
        Card other = activeCardClosingOn(20);
        transactionOn(other, LocalDateTime.of(2026, 3, 1, 12, 0), 25_000);

        CycleClosingSummary summary = useCase.closeCyclesFor(CLOSING_DATE);

        assertThat(summary.cardsConsidered()).isZero();
        assertThat(summary.invoicesClosed()).isZero();
        assertThat(invoices.all()).isEmpty();
    }

    @Test
    @DisplayName("considers a card with nothing to bill but closes no invoice for it")
    void skipsCardWithNoTransactions() {
        activeCardClosingOn(15);

        CycleClosingSummary summary = useCase.closeCyclesFor(CLOSING_DATE);

        assertThat(summary.cardsConsidered()).isEqualTo(1);
        assertThat(summary.invoicesClosed()).isZero();
        assertThat(summary.totalBilled()).isEqualTo(Money.ZERO);
        assertThat(invoices.all()).isEmpty();
    }

    @Test
    @DisplayName("bills the month before closing, excluding closing day itself")
    void billsOnlyTheCycleWindow() {
        Card card = activeCardClosingOn(15);
        transactionOn(card, LocalDateTime.of(2026, 2, 14, 23, 0), 99_000);  // just before the window
        transactionOn(card, LocalDateTime.of(2026, 2, 15, 0, 0), 10_000);   // first instant of the window
        transactionOn(card, LocalDateTime.of(2026, 3, 14, 23, 0), 25_000);  // last day inside
        transactionOn(card, LocalDateTime.of(2026, 3, 15, 0, 0), 88_000);   // closing day belongs to next cycle

        useCase.closeCyclesFor(CLOSING_DATE);

        assertThat(invoices.all().getFirst().totalAmount()).isEqualTo(Money.ofCents(35_000));
    }

    @Test
    @DisplayName("never bills a transaction twice, so closing the same cycle again is a no-op")
    void doesNotRebillAlreadyBilledTransactions() {
        Card card = activeCardClosingOn(15);
        transactionOn(card, LocalDateTime.of(2026, 3, 1, 12, 0), 25_000);
        useCase.closeCyclesFor(CLOSING_DATE);

        CycleClosingSummary rerun = useCase.closeCyclesFor(CLOSING_DATE);

        assertThat(rerun.invoicesClosed()).isZero();
        assertThat(invoices.all()).hasSize(1);
    }

    @Test
    @DisplayName("closes one invoice per card sharing today's cycle day")
    void closesOneInvoicePerCard() {
        Card first = activeCardClosingOn(15);
        Card second = activeCardClosingOn(15);
        transactionOn(first, LocalDateTime.of(2026, 3, 1, 12, 0), 10_000);
        transactionOn(second, LocalDateTime.of(2026, 3, 2, 12, 0), 20_000);

        CycleClosingSummary summary = useCase.closeCyclesFor(CLOSING_DATE);

        assertThat(summary.cardsConsidered()).isEqualTo(2);
        assertThat(summary.invoicesClosed()).isEqualTo(2);
        assertThat(summary.totalBilled()).isEqualTo(Money.ofCents(30_000));
    }
}

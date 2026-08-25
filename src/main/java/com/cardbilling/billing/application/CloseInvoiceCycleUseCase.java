package com.cardbilling.billing.application;

import com.cardbilling.billing.application.port.CardRepositoryPort;
import com.cardbilling.billing.application.port.CardTransactionRepositoryPort;
import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.BillingCycle;
import com.cardbilling.billing.domain.CardTransaction;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.Money;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the billing cycle for every active card whose cycle day is the given date: gathers the
 * transactions in the cycle window that no invoice has claimed, sums them into a new invoice, and
 * assigns each transaction to it.
 *
 * <p>Ported from the legacy monolith's {@code InvoiceClosingJob} with the rules unchanged - the
 * cycle window, the ten days to due date, and skipping a card with nothing to bill all behave
 * exactly as they did. What changed is that the card and its cardholder arrive from one query
 * instead of a lazy walk per invoice, which is what made the legacy's first live run throw a
 * LazyInitializationException.
 */
@Service
public class CloseInvoiceCycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(CloseInvoiceCycleUseCase.class);

    private final CardRepositoryPort cardRepository;
    private final CardTransactionRepositoryPort transactionRepository;
    private final InvoiceRepositoryPort invoiceRepository;

    public CloseInvoiceCycleUseCase(CardRepositoryPort cardRepository,
            CardTransactionRepositoryPort transactionRepository, InvoiceRepositoryPort invoiceRepository) {
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public CycleClosingSummary closeCyclesFor(LocalDate closingDate) {
        BillingCycle cycle = BillingCycle.closingOn(closingDate);
        List<BillableCard> cards = cardRepository.findActiveCardsClosingOn(closingDate.getDayOfMonth());

        int invoicesClosed = 0;
        Money totalBilled = Money.ZERO;
        for (BillableCard billableCard : cards) {
            Invoice closed = closeCycleFor(billableCard, cycle);
            if (closed != null) {
                invoicesClosed++;
                totalBilled = totalBilled.plus(closed.totalAmount());
            }
        }

        log.info("Closed {} invoices totalling {} across {} cards with a cycle day of {}",
                invoicesClosed, totalBilled, cards.size(), closingDate.getDayOfMonth());
        return CycleClosingSummary.of(closingDate, cards.size(), invoicesClosed, totalBilled);
    }

    /** @return the invoice closed, or null when the card had nothing to bill this cycle */
    private Invoice closeCycleFor(BillableCard billableCard, BillingCycle cycle) {
        List<CardTransaction> transactions = transactionRepository.findUnbilledForCardBetween(
                billableCard.cardId(), cycle.windowStart(), cycle.windowEnd());
        if (transactions.isEmpty()) {
            return null;
        }

        Money total = transactions.stream()
                .map(CardTransaction::amount)
                .reduce(Money.ZERO, Money::plus);

        Invoice invoice = invoiceRepository.save(
                Invoice.close(billableCard.cardId(), billableCard.cardholderDocument(), cycle, total));

        transactionRepository.saveAll(transactions.stream()
                .map(transaction -> transaction.assignedTo(invoice))
                .toList());
        return invoice;
    }
}

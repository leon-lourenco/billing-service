package com.cardbilling.billing.application;

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
import java.time.LocalDateTime;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty database with a synthetic but realistically-shaped card issuer dataset: 150
 * customers, one account and one card each, and four months of transaction history per card -
 * enough for cycle closing to have real cycles to close and for invoices to genuinely fall
 * overdue afterwards.
 *
 * <p>Ported from the legacy monolith's {@code DataSeeder}, deliberately down to the random draw
 * order, so the same seed produces the same dataset on both sides and a number measured against
 * one is comparable with the other.
 *
 * <p>A no-op once any customer exists - this only ever seeds an empty database.
 */
@Service
public class SeedDemoDataUseCase {

    private static final Logger log = LoggerFactory.getLogger(SeedDemoDataUseCase.class);

    private static final long RANDOM_SEED = 42L;
    private static final int CUSTOMER_COUNT = 150;
    private static final int MONTHS_OF_HISTORY = 4;
    private static final int DAYS_PER_STEP = 7;

    private static final String[] FIRST_NAMES = {
            "Ana", "Bruno", "Carla", "Diego", "Elisa", "Fabio", "Gabriela", "Heitor", "Isabela", "Joao",
            "Larissa", "Marcos", "Natalia", "Otavio", "Patricia", "Rafael", "Sabrina", "Thiago", "Vanessa", "Wesley"
    };
    private static final String[] LAST_NAMES = {
            "Almeida", "Barbosa", "Cardoso", "Duarte", "Ferreira", "Goncalves", "Henriques", "Lima",
            "Martins", "Nogueira", "Oliveira", "Pereira", "Ribeiro", "Santos", "Teixeira", "Vieira"
    };
    private static final String[] MERCHANTS = {
            "Mercado Bom Preco", "Farmacia Popular", "Posto Ipiranga", "Restaurante Sabor Caseiro",
            "Livraria Cultura", "Loja de Roupas Vestir Bem", "Assinatura Streaming", "Padaria Pao Quente",
            "Academia Corpo Ativo", "Pet Shop Amigo Fiel"
    };

    private final CustomerRepositoryPort customerRepository;
    private final AccountRepositoryPort accountRepository;
    private final CardRepositoryPort cardRepository;
    private final CardTransactionRepositoryPort transactionRepository;

    public SeedDemoDataUseCase(CustomerRepositoryPort customerRepository, AccountRepositoryPort accountRepository,
            CardRepositoryPort cardRepository, CardTransactionRepositoryPort transactionRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * @param now the instant history is generated backwards from - passed in rather than read
     *        from the clock so a test can seed a dataset whose cycles have already closed
     * @return how many transactions were written, or zero if the database was already seeded
     */
    @Transactional
    public int seed(LocalDateTime now) {
        if (customerRepository.count() > 0) {
            log.info("Database already seeded - skipping");
            return 0;
        }

        Random random = new Random(RANDOM_SEED);
        int transactionCount = 0;
        for (int index = 0; index < CUSTOMER_COUNT; index++) {
            Customer customer = seedCustomer(random, index);
            Account account = seedAccount(customer, index, now);
            Card card = seedCard(random, account, index);
            transactionCount += seedTransactions(random, card, now);
        }

        log.info("Seeded {} customers, each with one account and one card, and {} transactions in total",
                CUSTOMER_COUNT, transactionCount);
        return transactionCount;
    }

    private Customer seedCustomer(Random random, int index) {
        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        String fullName = firstName + " " + lastName;
        String email = (firstName + "." + lastName + index).toLowerCase() + "@example.com";
        DocumentNumber documentNumber = DocumentNumber.of(String.format("%011d", 10_000_000_000L + index));
        String phoneNumber = String.format("+55119%08d", random.nextInt(100_000_000));
        return customerRepository.save(
                Customer.register(fullName, documentNumber, email, phoneNumber, LocalDateTime.now()));
    }

    private Account seedAccount(Customer customer, int index, LocalDateTime now) {
        return accountRepository.save(
                Account.open(customer.id(), String.format("ACC-%06d", index), now.minusMonths(MONTHS_OF_HISTORY)));
    }

    private Card seedCard(Random random, Account account, int index) {
        String cardNumberMasked = String.format("**** **** **** %04d", 1000 + (index % 9000));
        // Between R$2,000.00 and R$9,990.00, in cents. Kept as the legacy computed it, including
        // the trailing x10 that its own comment didn't account for.
        Money creditLimit = Money.ofCents((200 + random.nextInt(800)) * 100L * 10);
        int billingCycleDay = 1 + random.nextInt(28);
        return cardRepository.save(Card.issue(account.id(), cardNumberMasked, creditLimit, billingCycleDay));
    }

    private int seedTransactions(Random random, Card card, LocalDateTime now) {
        int count = 0;
        LocalDateTime cursor = now.minusMonths(MONTHS_OF_HISTORY);
        while (cursor.isBefore(now)) {
            int transactionsThisWeek = random.nextInt(3);
            for (int i = 0; i < transactionsThisWeek; i++) {
                String merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];
                Money amount = Money.ofCents((10 + random.nextInt(490)) * 100L);
                transactionRepository.save(CardTransaction.record(card.id(), merchant, amount, cursor));
                count++;
            }
            cursor = cursor.plusDays(DAYS_PER_STEP);
        }
        return count;
    }
}

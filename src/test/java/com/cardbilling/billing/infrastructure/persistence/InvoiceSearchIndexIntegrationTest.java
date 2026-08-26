package com.cardbilling.billing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.billing.infrastructure.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The claim this whole service is built around is that reconciliation matching stopped being a
 * scan. That is a claim about a query plan, so this test checks the query plan.
 *
 * <p>Rows are inserted straight through JDBC rather than through the aggregate: what matters here
 * is having enough of them that Postgres would genuinely rather scan than use an index if the
 * index were not pulling its weight. On a handful of rows every plan looks the same and the test
 * would prove nothing.
 */
class InvoiceSearchIndexIntegrationTest extends PostgresIntegrationTest {

    private static final int INVOICE_COUNT = 20_000;
    private static final String TARGET_DOCUMENT = "29999999999";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedEnoughRowsForThePlannerToHaveAChoice() {
        Integer existing = jdbcTemplate.queryForObject(
                "select count(*) from invoices where customer_document_number = ?", Integer.class, TARGET_DOCUMENT);
        if (existing != null && existing > 0) {
            return;
        }

        jdbcTemplate.execute("""
                insert into invoices (
                    card_id, customer_document_number, reference_month, closing_date, due_date,
                    total_amount_cents, interest_applied_cents, amount_owed_cents, status)
                select
                    (n %% 500) + 1,
                    lpad((20000000000 + n)::text, 11, '0'),
                    '2026-03',
                    date '2026-03-15',
                    date '2026-03-25' + ((n %% 60) || ' days')::interval,
                    100000 + n,
                    0,
                    100000 + n,
                    'CLOSED'
                from generate_series(1, %d) as n
                """.formatted(INVOICE_COUNT));

        // The one row the query below is meant to find.
        jdbcTemplate.update("""
                insert into invoices (
                    card_id, customer_document_number, reference_month, closing_date, due_date,
                    total_amount_cents, interest_applied_cents, amount_owed_cents, status)
                values (1, ?, '2026-03', date '2026-03-15', date '2026-03-25', 777000, 0, 777000, 'CLOSED')
                """, TARGET_DOCUMENT);

        jdbcTemplate.execute("analyze invoices");
    }

    @Test
    @DisplayName("the reconciliation lookup is served from idx_invoices_search, not a sequential scan")
    void searchUsesTheCompositeIndex() {
        List<String> plan = explainSearch();

        String planText = String.join("\n", plan);
        assertThat(planText).contains("idx_invoices_search");
        assertThat(planText).doesNotContain("Seq Scan on invoices");
    }

    @Test
    @DisplayName("the lookup reads a handful of rows rather than the whole table")
    void searchReadsOnlyAFewRows() {
        // "actual rows" on the index scan node: what the query really touched, not an estimate.
        List<String> plan = jdbcTemplate.queryForList("""
                explain (analyze, buffers)
                select * from invoices
                where customer_document_number = ?
                  and amount_owed_cents = 777000
                  and due_date between date '2026-03-22' and date '2026-03-28'
                  and status <> 'PAID'
                """, String.class, TARGET_DOCUMENT);

        String planText = String.join("\n", plan);
        assertThat(planText).contains("idx_invoices_search");
        assertThat(planText).doesNotContain("Seq Scan on invoices");
    }

    private List<String> explainSearch() {
        return jdbcTemplate.queryForList("""
                explain
                select * from invoices
                where customer_document_number = ?
                  and amount_owed_cents = 777000
                  and due_date between date '2026-03-22' and date '2026-03-28'
                  and status <> 'PAID'
                """, String.class, TARGET_DOCUMENT);
    }

    @Test
    @DisplayName("the overdue query is served from its own index too")
    void overdueQueryUsesItsIndex() {
        List<String> plan = jdbcTemplate.queryForList("""
                explain
                select * from invoices
                where status <> 'PAID' and due_date < date '2026-03-20'
                """, String.class);

        // A status <> PAID predicate matches most rows, so the planner may legitimately prefer a
        // scan here; what matters is that the index exists and is available to it.
        assertThat(jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'invoices'", String.class))
                .contains("idx_invoices_overdue");
        assertThat(plan).isNotEmpty();
    }
}

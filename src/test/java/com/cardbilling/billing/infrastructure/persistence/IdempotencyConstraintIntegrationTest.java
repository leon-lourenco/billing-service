package com.cardbilling.billing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardbilling.billing.application.port.InvoiceRepositoryPort;
import com.cardbilling.billing.domain.BillingCycle;
import com.cardbilling.billing.domain.Cardholder;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.Invoice;
import com.cardbilling.billing.domain.Money;
import com.cardbilling.billing.infrastructure.PostgresIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The use cases already refuse a replayed accrual or payment. These tests go around them and
 * write straight to the tables, because the point of the constraints is to hold when something
 * gets past the application check - two concurrent requests, or a future caller that forgets.
 */
class IdempotencyConstraintIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private InvoiceRepositoryPort invoices;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long anInvoiceId(String documentNumber) {
        return invoices.save(Invoice.close(1L, Cardholder.of(1L, DocumentNumber.of(documentNumber)),
                BillingCycle.closingOn(LocalDate.of(2026, 3, 15)), Money.ofCents(100_000))).id();
    }

    @Test
    @DisplayName("the database refuses a second interest accrual for the same invoice and day")
    void rejectsDuplicateAccrualForSameDay() {
        Long invoiceId = anInvoiceId("10000000070");
        insertAccrual(invoiceId, "2026-03-26");

        assertThatThrownBy(() -> insertAccrual(invoiceId, "2026-03-26"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("but allows accruals for the same invoice on different days")
    void allowsAccrualsOnDifferentDays() {
        Long invoiceId = anInvoiceId("10000000071");
        insertAccrual(invoiceId, "2026-03-26");
        insertAccrual(invoiceId, "2026-03-27");

        assertThat(countAccruals(invoiceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("the database refuses a second payment carrying the same external reference")
    void rejectsDuplicateExternalReference() {
        Long invoiceId = anInvoiceId("10000000072");
        insertPayment(invoiceId, "STMT-CONSTRAINT-1");

        assertThatThrownBy(() -> insertPayment(invoiceId, "STMT-CONSTRAINT-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a replay aimed at a different invoice is refused just the same")
    void rejectsDuplicateReferenceAcrossInvoices() {
        Long first = anInvoiceId("10000000073");
        Long second = anInvoiceId("10000000074");
        insertPayment(first, "STMT-CONSTRAINT-2");

        assertThatThrownBy(() -> insertPayment(second, "STMT-CONSTRAINT-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("internal payments carry no reference, so any number of them is fine")
    void allowsManyPaymentsWithoutAReference() {
        Long invoiceId = anInvoiceId("10000000075");
        insertInternalPayment(invoiceId);
        insertInternalPayment(invoiceId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payments where invoice_id = ?", Integer.class, invoiceId))
                .isEqualTo(2);
    }

    private void insertAccrual(Long invoiceId, String accrualDate) {
        jdbcTemplate.update("""
                insert into interest_accruals (invoice_id, accrual_date, late_fee_cents, daily_interest_cents)
                values (?, ?::date, 2000, 1000)
                """, invoiceId, accrualDate);
    }

    private void insertPayment(Long invoiceId, String externalReference) {
        jdbcTemplate.update("""
                insert into payments (invoice_id, amount_cents, paid_at, source, external_reference)
                values (?, 10000, timestamp '2026-03-26 10:00:00', 'EXTERNAL_RECONCILIATION', ?)
                """, invoiceId, externalReference);
    }

    /**
     * Deliberately INTERNAL rather than a reconciled payment with a null reference. Writing the
     * latter is how this test first failed: the rows are legal as far as the unique constraint is
     * concerned, but no reconciled payment can exist without the statement line it came from, so
     * the domain refused to load them back and every later query over those invoices blew up.
     */
    private void insertInternalPayment(Long invoiceId) {
        jdbcTemplate.update("""
                insert into payments (invoice_id, amount_cents, paid_at, source, external_reference)
                values (?, 10000, timestamp '2026-03-26 10:00:00', 'INTERNAL', null)
                """, invoiceId);
    }

    private Integer countAccruals(Long invoiceId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from interest_accruals where invoice_id = ?", Integer.class, invoiceId);
    }
}

package com.cardbilling.billing.domain;

import static com.cardbilling.billing.domain.InvoiceFixtures.CLOSING_DATE;
import static com.cardbilling.billing.domain.InvoiceFixtures.DUE_DATE;
import static com.cardbilling.billing.domain.InvoiceFixtures.closedInvoiceOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    @Test
    @DisplayName("a closed invoice owes its cycle total and nothing more")
    void closesWithCycleTotal() {
        Invoice invoice = closedInvoiceOf(50_000);

        assertThat(invoice.status()).isEqualTo(Invoice.Status.CLOSED);
        assertThat(invoice.totalAmount()).isEqualTo(Money.ofCents(50_000));
        assertThat(invoice.interestApplied()).isEqualTo(Money.ZERO);
        assertThat(invoice.amountOwed()).isEqualTo(Money.ofCents(50_000));
        assertThat(invoice.amountDue()).isEqualTo(Money.ofCents(50_000));
        assertThat(invoice.referenceMonth()).isEqualTo("2026-03");
        assertThat(invoice.dueDate()).isEqualTo(DUE_DATE);
    }

    @Test
    @DisplayName("carries both cardholder identifiers - the id to act on, the document to match on")
    void carriesBothCardholderIdentifiers() {
        Invoice invoice = closedInvoiceOf(50_000);

        assertThat(invoice.customerId()).isEqualTo(InvoiceFixtures.CUSTOMER_ID);
        assertThat(invoice.customerDocumentNumber()).isEqualTo(InvoiceFixtures.DOCUMENT);
        assertThat(invoice.cardholder()).isEqualTo(InvoiceFixtures.CARDHOLDER);
    }

    @Nested
    @DisplayName("interest accrual")
    class InterestAccrualBehaviour {

        @Test
        @DisplayName("adds the charge to what is owed and marks the invoice overdue")
        void accruesAndMarksOverdue() {
            Invoice invoice = closedInvoiceOf(50_000);

            invoice.accrueInterest(InterestCharge.of(Money.ofCents(1_000), Money.ofCents(500)), DUE_DATE.plusDays(1));

            assertThat(invoice.status()).isEqualTo(Invoice.Status.OVERDUE);
            assertThat(invoice.interestApplied()).isEqualTo(Money.ofCents(1_500));
            assertThat(invoice.amountOwed()).isEqualTo(Money.ofCents(51_500));
            assertThat(invoice.lastInterestAccrualDate()).contains(DUE_DATE.plusDays(1));
        }

        @Test
        @DisplayName("refuses a second charge for a day already accrued")
        void refusesDuplicateAccrualForSameDay() {
            Invoice invoice = closedInvoiceOf(50_000);
            LocalDate accrualDate = DUE_DATE.plusDays(1);
            invoice.accrueInterest(InterestCharge.of(Money.ofCents(1_000), Money.ofCents(500)), accrualDate);

            assertThatThrownBy(() -> invoice.accrueInterest(
                    InterestCharge.of(Money.ZERO, Money.ofCents(500)), accrualDate))
                    .isInstanceOf(InterestAlreadyAccruedException.class)
                    .hasMessageContaining("already accrued interest for " + accrualDate);

            assertThat(invoice.interestApplied()).isEqualTo(Money.ofCents(1_500));
            assertThat(invoice.interestAccruals()).hasSize(1);
        }

        @Test
        @DisplayName("refuses a backdated charge for a day already accrued, not just the most recent one")
        void refusesDuplicateAccrualForAnEarlierDay() {
            Invoice invoice = closedInvoiceOf(50_000);
            LocalDate firstDay = DUE_DATE.plusDays(1);
            invoice.accrueInterest(InterestCharge.of(Money.ofCents(1_000), Money.ofCents(500)), firstDay);
            invoice.accrueInterest(InterestCharge.of(Money.ZERO, Money.ofCents(500)), DUE_DATE.plusDays(2));

            // The legacy tracked only the latest accrual date, so replaying an earlier day slipped through.
            assertThatThrownBy(() -> invoice.accrueInterest(
                    InterestCharge.of(Money.ZERO, Money.ofCents(500)), firstDay))
                    .isInstanceOf(InterestAlreadyAccruedException.class);

            assertThat(invoice.interestAccruals()).hasSize(2);
        }

        @Test
        @DisplayName("accrues day after day without compounding")
        void accruesDayAfterDayWithoutCompounding() {
            Invoice invoice = closedInvoiceOf(50_000);
            InterestPolicy policy = new InterestPolicy();

            invoice.accrueInterest(policy.chargeFor(invoice), DUE_DATE.plusDays(1));
            invoice.accrueInterest(policy.chargeFor(invoice), DUE_DATE.plusDays(2));
            invoice.accrueInterest(policy.chargeFor(invoice), DUE_DATE.plusDays(3));

            // 2% fee once (1000) + 1% of the original 50000 on each of three days (500 x 3).
            assertThat(invoice.interestApplied()).isEqualTo(Money.ofCents(2_500));
            assertThat(invoice.amountOwed()).isEqualTo(Money.ofCents(52_500));
        }

        @Test
        @DisplayName("keeps the latest accrual date when days arrive out of order")
        void keepsLatestAccrualDate() {
            Invoice invoice = closedInvoiceOf(50_000);
            invoice.accrueInterest(InterestCharge.of(Money.ZERO, Money.ofCents(500)), DUE_DATE.plusDays(5));
            invoice.accrueInterest(InterestCharge.of(Money.ZERO, Money.ofCents(500)), DUE_DATE.plusDays(2));

            assertThat(invoice.lastInterestAccrualDate()).contains(DUE_DATE.plusDays(5));
        }
    }

    @Nested
    @DisplayName("payments")
    class PaymentBehaviour {

        @Test
        @DisplayName("a payment covering the full amount owed marks the invoice paid")
        void fullPaymentMarksPaid() {
            Invoice invoice = closedInvoiceOf(50_000);

            invoice.recordPayment(Money.ofCents(50_000), LocalDateTime.of(2026, 3, 24, 10, 0),
                    Payment.Source.INTERNAL, null);

            assertThat(invoice.status()).isEqualTo(Invoice.Status.PAID);
            assertThat(invoice.isPaid()).isTrue();
            assertThat(invoice.amountDue()).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("a partial payment leaves the invoice unpaid and the remainder due")
        void partialPaymentLeavesRemainderDue() {
            Invoice invoice = closedInvoiceOf(50_000);

            invoice.recordPayment(Money.ofCents(20_000), LocalDateTime.of(2026, 3, 24, 10, 0),
                    Payment.Source.INTERNAL, null);

            assertThat(invoice.status()).isEqualTo(Invoice.Status.CLOSED);
            assertThat(invoice.amountPaid()).isEqualTo(Money.ofCents(20_000));
            assertThat(invoice.amountDue()).isEqualTo(Money.ofCents(30_000));
        }

        @Test
        @DisplayName("interest accrued after closing has to be covered too")
        void paymentMustCoverAccruedInterest() {
            Invoice invoice = closedInvoiceOf(50_000);
            invoice.accrueInterest(InterestCharge.of(Money.ofCents(1_000), Money.ofCents(500)), DUE_DATE.plusDays(1));

            invoice.recordPayment(Money.ofCents(50_000), LocalDateTime.of(2026, 3, 26, 10, 0),
                    Payment.Source.INTERNAL, null);

            assertThat(invoice.isPaid()).isFalse();
            assertThat(invoice.amountDue()).isEqualTo(Money.ofCents(1_500));
        }

        @Test
        @DisplayName("refuses a replayed reconciliation match")
        void refusesDuplicateExternalReference() {
            Invoice invoice = closedInvoiceOf(50_000);
            invoice.recordPayment(Money.ofCents(50_000), LocalDateTime.of(2026, 3, 24, 10, 0),
                    Payment.Source.EXTERNAL_RECONCILIATION, "STMT-0001");

            assertThatThrownBy(() -> invoice.recordPayment(Money.ofCents(50_000),
                    LocalDateTime.of(2026, 3, 24, 10, 0), Payment.Source.EXTERNAL_RECONCILIATION, "STMT-0001"))
                    .isInstanceOf(DuplicatePaymentException.class)
                    .hasMessageContaining("STMT-0001");

            assertThat(invoice.payments()).hasSize(1);
        }

        @Test
        @DisplayName("two internal payments without references are both recorded")
        void allowsMultipleUnreferencedPayments() {
            Invoice invoice = closedInvoiceOf(50_000);
            invoice.recordPayment(Money.ofCents(20_000), LocalDateTime.of(2026, 3, 20, 10, 0),
                    Payment.Source.INTERNAL, null);
            invoice.recordPayment(Money.ofCents(30_000), LocalDateTime.of(2026, 3, 22, 10, 0),
                    Payment.Source.INTERNAL, null);

            assertThat(invoice.payments()).hasSize(2);
            assertThat(invoice.isPaid()).isTrue();
        }

        @Test
        @DisplayName("rejects a payment for zero or a negative amount")
        void rejectsNonPositivePayment() {
            Invoice invoice = closedInvoiceOf(50_000);

            assertThatThrownBy(() -> invoice.recordPayment(Money.ZERO, LocalDateTime.of(2026, 3, 20, 10, 0),
                    Payment.Source.INTERNAL, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a reconciled payment must carry the statement line reference it came from")
        void reconciledPaymentRequiresReference() {
            Invoice invoice = closedInvoiceOf(50_000);

            assertThatThrownBy(() -> invoice.recordPayment(Money.ofCents(50_000),
                    LocalDateTime.of(2026, 3, 20, 10, 0), Payment.Source.EXTERNAL_RECONCILIATION, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("overdue")
    class OverdueBehaviour {

        @Test
        @DisplayName("is overdue only strictly after the due date")
        void overdueOnlyAfterDueDate() {
            Invoice invoice = closedInvoiceOf(50_000);

            assertThat(invoice.isOverdueAsOf(DUE_DATE)).isFalse();
            assertThat(invoice.isOverdueAsOf(DUE_DATE.plusDays(1))).isTrue();
            assertThat(invoice.isOverdueAsOf(CLOSING_DATE)).isFalse();
        }

        @Test
        @DisplayName("a paid invoice is never overdue, however late it was settled")
        void paidInvoiceIsNeverOverdue() {
            Invoice invoice = closedInvoiceOf(50_000);
            invoice.recordPayment(Money.ofCents(50_000), LocalDateTime.of(2026, 4, 30, 10, 0),
                    Payment.Source.INTERNAL, null);

            assertThat(invoice.isOverdueAsOf(DUE_DATE.plusDays(30))).isFalse();
        }
    }

    @Test
    @DisplayName("exposes its payments and accruals read-only - the aggregate owns its own contents")
    void collectionsAreUnmodifiable() {
        Invoice invoice = closedInvoiceOf(50_000);

        assertThatThrownBy(() -> invoice.payments().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> invoice.interestAccruals().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

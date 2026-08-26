package com.cardbilling.billing.infrastructure.persistence;

import com.cardbilling.billing.domain.Invoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity, Long> {

    /**
     * Loads the whole aggregate in one round trip. Every mutating use case immediately reads the
     * invoice's payments and accruals to decide whether the request is a replay, so fetching them
     * lazily would guarantee two extra queries and, outside a transaction, the same
     * LazyInitializationException the legacy hit on its first live run.
     */
    @EntityGraph(attributePaths = {"payments", "interestAccruals"})
    Optional<InvoiceEntity> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"payments", "interestAccruals"})
    List<InvoiceEntity> findByStatusNotAndDueDateBefore(Invoice.Status status, LocalDate dueDate);

    /**
     * The indexed reconciliation lookup. Equality on document number and amount owed, then a
     * range on due date - exactly the column order of {@code idx_invoices_search}, so Postgres
     * can serve it from that index rather than scanning open invoices.
     */
    @EntityGraph(attributePaths = {"payments", "interestAccruals"})
    @Query("""
            select invoice from InvoiceEntity invoice
            where invoice.customerDocumentNumber = :documentNumber
              and invoice.amountOwedCents = :amountOwedCents
              and invoice.dueDate between :earliestDueDate and :latestDueDate
              and invoice.status <> :paidStatus
            """)
    List<InvoiceEntity> search(
            @Param("documentNumber") String documentNumber,
            @Param("amountOwedCents") long amountOwedCents,
            @Param("earliestDueDate") LocalDate earliestDueDate,
            @Param("latestDueDate") LocalDate latestDueDate,
            @Param("paidStatus") Invoice.Status paidStatus);
}

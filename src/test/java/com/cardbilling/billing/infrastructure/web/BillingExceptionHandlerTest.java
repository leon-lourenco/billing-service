package com.cardbilling.billing.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.billing.domain.DuplicatePaymentException;
import com.cardbilling.billing.domain.InterestAlreadyAccruedException;
import com.cardbilling.billing.domain.InvoiceNotFoundException;
import com.cardbilling.billing.domain.MalformedValueException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * How each failure is classified. Worth testing directly rather than only through the API,
 * because the classification is the part that was wrong before: a Hibernate mapping fault was
 * being reported to callers as their own bad request.
 */
class BillingExceptionHandlerTest {

    private final BillingExceptionHandler handler = new BillingExceptionHandler();

    @Test
    @DisplayName("an unknown invoice is a 404 carrying the id that was asked for")
    void unknownInvoiceIsNotFound() {
        ProblemDetail problem = handler.handleInvoiceNotFound(new InvoiceNotFoundException(42L));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Invoice not found");
        assertThat(problem.getProperties()).containsEntry("invoiceId", 42L);
    }

    @Test
    @DisplayName("malformed input is a 400 naming the field at fault")
    void malformedValueIsBadRequest() {
        ProblemDetail problem = handler.handleMalformedValue(
                new MalformedValueException("documentNumber", "Document number must be exactly 11 digits"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getProperties()).containsEntry("field", "documentNumber");
    }

    @Test
    @DisplayName("a duplicate payment is a conflict, not a fault")
    void duplicatePaymentIsAConflict() {
        ProblemDetail problem = handler.handleDuplicatePayment(new DuplicatePaymentException("STMT-1"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("externalReference", "STMT-1");
    }

    @Test
    @DisplayName("a repeated accrual is a conflict too")
    void repeatedAccrualIsAConflict() {
        ProblemDetail problem = handler.handleInterestAlreadyAccrued(
                new InterestAlreadyAccruedException(7L, LocalDate.of(2026, 3, 26)));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("accrualDate", "2026-03-26");
    }

    @Test
    @DisplayName("a constraint violation is reported as a conflict")
    void constraintViolationIsAConflict() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    @DisplayName("an internal data-access fault is a 500, never blamed on the caller")
    void dataAccessFaultIsInternal() {
        // This is the regression: a mapping mistake used to surface as a 400, telling a caller
        // its perfectly valid request was malformed.
        ProblemDetail problem = handler.handleDataAccessFailure(
                new InvalidDataAccessApiUsageException("cannot simultaneously fetch multiple bags"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Internal error");
    }

    @Test
    @DisplayName("an internal fault's detail stays in the log rather than the response body")
    void internalFaultDetailIsNotLeaked() {
        ProblemDetail problem = handler.handleDataAccessFailure(
                new InvalidDataAccessApiUsageException("org.hibernate.loader.MultipleBagFetchException: ..."));

        assertThat(problem.getDetail()).isEqualTo("The request could not be completed");
        assertThat(problem.getDetail()).doesNotContain("Hibernate");
    }
}

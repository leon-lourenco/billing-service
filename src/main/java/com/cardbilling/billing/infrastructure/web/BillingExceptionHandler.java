package com.cardbilling.billing.infrastructure.web;

import com.cardbilling.billing.domain.DuplicatePaymentException;
import com.cardbilling.billing.domain.InterestAlreadyAccruedException;
import com.cardbilling.billing.domain.InvoiceNotFoundException;
import com.cardbilling.billing.domain.MalformedValueException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps this service's failures to RFC 7807 {@code application/problem+json}, using Spring's
 * built-in {@link ProblemDetail}.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own failures - a missing
 * request parameter, an unparseable date - keep the statuses they should have and come back as
 * problem documents too, rather than being swallowed by a catch-all here.
 *
 * <p>The classification that matters: a caller sending something we cannot make sense of is a
 * 400, and anything else escaping from inside this service is a 500. An earlier version of this
 * class handled {@code IllegalArgumentException} wholesale, which quietly reported a Hibernate
 * mapping fault as the caller's bad request - a 400 on a request that was perfectly valid. Broad
 * exception handlers do not just lose detail, they misattribute blame, so every entry below names
 * something specific.
 */
@RestControllerAdvice
class BillingExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BillingExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://cardbilling.example/problems/";

    @ExceptionHandler(InvoiceNotFoundException.class)
    ProblemDetail handleInvoiceNotFound(InvoiceNotFoundException exception) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Invoice not found", "invoice-not-found",
                exception.getMessage());
        problem.setProperty("invoiceId", exception.invoiceId());
        return problem;
    }

    /** Input that never formed a valid value - a malformed document number, for instance. */
    @ExceptionHandler(MalformedValueException.class)
    ProblemDetail handleMalformedValue(MalformedValueException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "invalid-request",
                exception.getMessage());
        problem.setProperty("field", exception.field());
        return problem;
    }

    /**
     * Reached only when two requests race past the use case's own check and the unique constraint
     * catches the loser - a sequential replay is answered 200 with {@code recorded: false}.
     */
    @ExceptionHandler(DuplicatePaymentException.class)
    ProblemDetail handleDuplicatePayment(DuplicatePaymentException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Payment already recorded", "duplicate-payment",
                exception.getMessage());
        problem.setProperty("externalReference", exception.externalReference());
        return problem;
    }

    /** Likewise the concurrent case; a sequential replay is answered 200 with {@code applied: false}. */
    @ExceptionHandler(InterestAlreadyAccruedException.class)
    ProblemDetail handleInterestAlreadyAccrued(InterestAlreadyAccruedException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Interest already accrued for this day",
                "interest-already-accrued", exception.getMessage());
        problem.setProperty("invoiceId", exception.invoiceId());
        problem.setProperty("accrualDate", exception.accrualDate().toString());
        return problem;
    }

    /**
     * The database rejecting a duplicate the application layer thought was new. A conflict rather
     * than a fault: it is what the idempotency constraints are there to do.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Rejected by a database constraint - a concurrent duplicate is the usual cause", exception);
        return problem(HttpStatus.CONFLICT, "Conflicting record", "conflicting-record",
                "This request conflicts with a record that already exists");
    }

    /**
     * Any other data-access failure is this service's problem, not the caller's, and is reported
     * as one - with the detail logged here and kept out of the response body.
     */
    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDataAccessFailure(DataAccessException exception) {
        log.error("Data access failed", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "internal-error",
                "The request could not be completed");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "invalid-request",
                "The request body failed validation");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private ProblemDetail problem(HttpStatus status, String title, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + type));
        return problem;
    }
}

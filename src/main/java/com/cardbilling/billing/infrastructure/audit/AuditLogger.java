package com.cardbilling.billing.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Audit logger for recording business-critical events.
 * All audit events are logged with structured JSON for centralization.
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    public void logPaymentRecorded(Long invoiceId, Long amountCents, String source,
                                   String externalReference, String userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "PAYMENT_RECORDED");
        event.put("invoiceId", invoiceId);
        event.put("amountCents", amountCents);
        event.put("source", source);
        event.put("externalReference", externalReference);
        event.put("userId", userId != null ? userId : "system");
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Payment recorded", event);
    }

    public void logInterestAccrued(Long invoiceId, Long feeCents, Long dailyInterestCents,
                                   String accrualDate, String userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "INTEREST_ACCRUED");
        event.put("invoiceId", invoiceId);
        event.put("feeCents", feeCents);
        event.put("dailyInterestCents", dailyInterestCents);
        event.put("totalCents", feeCents + dailyInterestCents);
        event.put("accrualDate", accrualDate);
        event.put("userId", userId != null ? userId : "system");
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Interest accrued", event);
    }

    public void logCycleClosed(String cycleDate, int invoiceCount, Long totalCents, String userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "CYCLE_CLOSED");
        event.put("cycleDate", cycleDate);
        event.put("invoiceCount", invoiceCount);
        event.put("totalCents", totalCents);
        event.put("userId", userId != null ? userId : "system");
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Billing cycle closed", event);
    }

    public void logInvoiceNotFound(Long invoiceId, String userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "INVOICE_NOT_FOUND");
        event.put("invoiceId", invoiceId);
        event.put("userId", userId != null ? userId : "system");
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.warn("Audit: Invoice not found", event);
    }

    public void logDuplicatePaymentAttempted(Long invoiceId, String externalReference, String userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "DUPLICATE_PAYMENT_ATTEMPTED");
        event.put("invoiceId", invoiceId);
        event.put("externalReference", externalReference);
        event.put("userId", userId != null ? userId : "system");
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.warn("Audit: Duplicate payment attempted (idempotent retry)", event);
    }
}

package com.cardbilling.billing.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Custom metrics for billing service business operations.
 */
@Component
public class BillingMetrics {

    private final MeterRegistry meterRegistry;

    public BillingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordInvoiceCycleClosed(int invoiceCount, long totalCents) {
        Counter.builder("invoice.cycle.closed")
            .description("Number of invoice cycles closed")
            .tag("service", "billing")
            .register(meterRegistry)
            .increment();

        meterRegistry.gauge("invoice.cycle.last.count", invoiceCount);
        meterRegistry.gauge("invoice.cycle.last.totalCents", totalCents);
    }

    public void recordPaymentRecorded(long amountCents, String source) {
        Counter.builder("payment.recorded")
            .description("Number of payments recorded")
            .tag("source", source)
            .register(meterRegistry)
            .increment();

        meterRegistry.counter("payment.recorded.cents", "source", source)
            .increment(amountCents);
    }

    public void recordInterestAccrued(long invoiceId, long feeCents, long interestCents, int daysOverdue) {
        Counter.builder("interest.accrued")
            .description("Number of interest accruals")
            .tag("daysOverdue", String.valueOf(daysOverdue))
            .register(meterRegistry)
            .increment();

        meterRegistry.counter("interest.accrued.cents",
            "type", daysOverdue > 0 ? "daily" : "fee")
            .increment(feeCents + interestCents);
    }

    public void recordSearchLatency(String searchType, long latencyMs) {
        Timer.builder("invoice.search")
            .description("Invoice search operation latency")
            .tag("type", searchType)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordOverdueDetected(int invoiceCount, long totalCents) {
        Counter.builder("invoice.overdue.detected")
            .description("Number of overdue invoices detected")
            .register(meterRegistry)
            .increment();

        meterRegistry.gauge("invoice.overdue.last.count", invoiceCount);
        meterRegistry.gauge("invoice.overdue.last.totalCents", totalCents);
    }
}

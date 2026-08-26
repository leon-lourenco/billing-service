package com.cardbilling.billing.infrastructure.health;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import java.time.Instant;

/**
 * Health indicator for database connectivity and basic query performance.
 */
@Component
public class DatabaseHealthIndicator extends AbstractHealthIndicator {

    private final EntityManager entityManager;

    public DatabaseHealthIndicator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            Instant start = Instant.now();

            // Execute a simple query to verify database connectivity
            Long count = (Long) entityManager.createQuery("SELECT COUNT(i) FROM Invoice i")
                .getSingleResult();

            long duration = java.time.Duration.between(start, Instant.now()).toMillis();

            builder.up()
                .withDetail("database", "PostgreSQL")
                .withDetail("invoiceCount", count)
                .withDetail("queryTime", duration + "ms");
        } catch (Exception ex) {
            builder.down()
                .withException(ex)
                .withDetail("error", ex.getMessage());
        }
    }
}

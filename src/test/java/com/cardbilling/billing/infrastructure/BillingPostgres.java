package com.cardbilling.billing.infrastructure;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container for the entire test run.
 *
 * <p>The per-class {@code @Container} lifecycle starts a fresh database for every Spring context,
 * which on a machine already running other containers is enough to push startup past the default
 * sixty-second readiness wait - which is exactly how this failed the first time. Starting it once
 * here and pointing every context at it keeps the whole suite to a single database, and the
 * generous startup timeout covers a loaded machine rather than assuming an idle one.
 *
 * <p>Nothing stops it deliberately: Testcontainers' own reaper removes it when the JVM exits.
 */
public final class BillingPostgres {

    private static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16")
            .withStartupTimeout(Duration.ofMinutes(3));

    static {
        INSTANCE.start();
    }

    private BillingPostgres() {
    }

    /** Points a Spring context's datasource at the shared container. */
    public static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", INSTANCE::getUsername);
        registry.add("spring.datasource.password", INSTANCE::getPassword);
    }
}

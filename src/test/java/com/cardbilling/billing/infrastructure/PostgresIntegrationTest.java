package com.cardbilling.billing.infrastructure;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Base for the tests that need a real database rather than a fake one - the mapping, the queries
 * and the constraints are exactly the things an in-memory fake cannot tell the truth about.
 *
 * <p>Runs under the {@code local} profile, so these exercise routing and business behaviour
 * without a token in the way; {@code SecurityIntegrationTest} covers the default secured
 * configuration on its own. Seeding is off: each test sets up the handful of rows it reasons
 * about.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "billing.seed-demo-data=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public abstract class PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BillingPostgres.registerDatasource(registry);
    }
}

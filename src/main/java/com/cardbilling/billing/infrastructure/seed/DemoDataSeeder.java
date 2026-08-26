package com.cardbilling.billing.infrastructure.seed;

import com.cardbilling.billing.application.SeedDemoDataUseCase;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the demo seed on startup. Disable with {@code billing.seed-demo-data=false} - which the
 * tests do, since they set up exactly the data each one needs.
 */
@Component
@ConditionalOnProperty(name = "billing.seed-demo-data", havingValue = "true", matchIfMissing = true)
class DemoDataSeeder implements CommandLineRunner {

    private final SeedDemoDataUseCase seedDemoData;

    DemoDataSeeder(SeedDemoDataUseCase seedDemoData) {
        this.seedDemoData = seedDemoData;
    }

    @Override
    public void run(String... args) {
        seedDemoData.seed(LocalDateTime.now());
    }
}

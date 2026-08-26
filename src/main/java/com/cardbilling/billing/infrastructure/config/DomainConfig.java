package com.cardbilling.billing.infrastructure.config;

import com.cardbilling.billing.domain.InterestPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the domain services as beans from out here, so the domain layer itself stays free of
 * framework annotations and the ArchUnit rule forbidding framework imports in {@code domain}
 * stays honest.
 */
@Configuration
public class DomainConfig {

    @Bean
    InterestPolicy interestPolicy() {
        return new InterestPolicy();
    }
}

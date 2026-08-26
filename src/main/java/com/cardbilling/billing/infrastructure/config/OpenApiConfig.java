package com.cardbilling.billing.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI billingServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("billing-service")
                        .version("0.1.0")
                        .description("""
                                Owns the invoice lifecycle for card-billing-modernization: closing a \
                                billing cycle into an invoice, charging interest on overdue invoices, \
                                and recording payments. Every other service calls into this one; it \
                                calls no one."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Keycloak client-credentials access token")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}

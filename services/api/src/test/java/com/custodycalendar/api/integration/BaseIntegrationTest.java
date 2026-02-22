package com.custodycalendar.api.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
public abstract class BaseIntegrationTest {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("INTEGRATION_DB_URL", "jdbc:postgresql://localhost:5432/custody_calendar"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("INTEGRATION_DB_USERNAME", "custody"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("INTEGRATION_DB_PASSWORD", "custody"));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:9999/jwks");
    }
}

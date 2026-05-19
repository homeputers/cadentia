package com.cadentia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CadentiaApplicationContextCanaryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("CADENTIA_DB_URL", POSTGRES::getJdbcUrl);
        registry.add("CADENTIA_DB_USERNAME", POSTGRES::getUsername);
        registry.add("CADENTIA_DB_PASSWORD", POSTGRES::getPassword);
    }

    @Test
    void contextLoads() {
        // Canary test: verifies Spring can instantiate all beans with production wiring.
    }
}

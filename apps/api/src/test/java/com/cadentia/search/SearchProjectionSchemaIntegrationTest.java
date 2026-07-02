package com.cadentia.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class SearchProjectionSchemaIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    @Test
    void initializesPostgresSearchExtensionsAndSeparatedProjectionTables() {
        // Arrange / Act
        Integer installedExtensionCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_extension
                WHERE extname IN ('pg_trgm', 'unaccent')
                """, Map.of(), Integer.class);
        Integer approvedProjectionCount = countTable("approved_search_documents");
        Integer adminProjectionCount = countTable("admin_review_search_documents");

        // Assert
        assertThat(installedExtensionCount).isEqualTo(2);
        assertThat(approvedProjectionCount).isEqualTo(1);
        assertThat(adminProjectionCount).isEqualTo(1);
    }

    @Test
    void searchProjectionDoesNotReplaceRecommendationReadModel() {
        // Arrange / Act
        Integer recommendableViewCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.views
                WHERE table_schema = 'public'
                    AND table_name = 'v_recommendable_arrangements'
                """, Map.of(), Integer.class);
        Integer searchProjectionCount = countTable("approved_search_documents");

        // Assert
        assertThat(recommendableViewCount).isEqualTo(1);
        assertThat(searchProjectionCount).isEqualTo(1);
    }

    private Integer countTable(String tableName) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                    AND table_name = :tableName
                """, Map.of("tableName", tableName), Integer.class);
    }
}

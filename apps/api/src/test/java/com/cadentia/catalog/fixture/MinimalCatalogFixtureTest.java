package com.cadentia.catalog.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MinimalCatalogFixtureTest {

    @Test
    void adr001FixtureIsTestScopedAndExercisesCompleteCatalogFlow() throws IOException {
        // Arrange
        String fixtureSql = readResource("db/fixtures/adr001_minimal_catalog_fixture.sql");

        // Act / Assert
        assertThat(fixtureSql)
                .contains("INSERT INTO songs")
                .contains("INSERT INTO arrangements")
                .contains("INSERT INTO lyrics_documents")
                .contains("INSERT INTO tags")
                .contains("INSERT INTO song_tags")
                .contains("INSERT INTO arrangement_tags")
                .contains("INSERT INTO import_batches")
                .contains("INSERT INTO provenance_records")
                .contains("INSERT INTO approval_records");
    }

    @Test
    void adr001FixtureClearlyPreventsProductionRecommendationUse() throws IOException {
        // Arrange
        String fixtureSql = readResource("db/fixtures/adr001_minimal_catalog_fixture.sql");

        // Act / Assert
        assertThat(fixtureSql)
                .contains("[TEST FIXTURE]")
                .contains("'DRAFT'")
                .contains("'TEST_FIXTURE'")
                .contains("productionApproved")
                .contains("externalServices")
                .contains("'PENDING'")
                .contains("'NEEDS_REVIEW'")
                .doesNotContain("'APPROVED'");
    }

    @Test
    void adr001FixtureResetScriptIsAvailableForAutomatedTests() throws IOException {
        // Arrange / Act
        String resetSql = readResource("db/fixtures/reset_adr001_minimal_catalog_fixture.sql");

        // Assert
        assertThat(resetSql)
                .contains("DELETE FROM approval_records")
                .contains("DELETE FROM provenance_records")
                .contains("DELETE FROM arrangement_tags")
                .contains("DELETE FROM song_tags")
                .contains("DELETE FROM lyrics_documents")
                .contains("DELETE FROM arrangements")
                .contains("DELETE FROM tags")
                .contains("DELETE FROM songs")
                .contains("DELETE FROM import_batches");
    }

    private static String readResource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}

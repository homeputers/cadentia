package com.cadentia.catalog.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ImportCandidateSchemaTest {

    @Test
    void adr003MigrationStagesRawCandidatesOutsideCanonicalSongs() throws IOException {
        // Arrange / Act
        String migrationSql = readResource("db/migration/V003__import_candidate_deduplication_schema.sql");

        // Assert
        assertThat(migrationSql)
                .contains("CREATE TABLE import_candidates")
                .contains("import_batch_id uuid NOT NULL REFERENCES import_batches")
                .contains("normalized_title varchar(255) NOT NULL")
                .contains("source_artist_metadata jsonb NOT NULL")
                .contains("ccli_number varchar(64)")
                .contains("lyrics_hash varchar(128)")
                .contains("source_payload jsonb NOT NULL")
                .contains("status IN ('STAGED', 'DEDUPLICATION_REVIEW', 'READY_TO_MERGE', 'MERGED', 'REJECTED', 'FAILED')")
                .contains("these rows are never recommendable canonical catalog content")
                .doesNotContain("INSERT INTO songs");
    }

    @Test
    void adr003MigrationSeparatesProposedMatchesFromManualReviewDecisions() throws IOException {
        // Arrange / Act
        String migrationSql = readResource("db/migration/V003__import_candidate_deduplication_schema.sql");

        // Assert
        assertThat(migrationSql)
                .contains("CREATE TABLE proposed_duplicate_matches")
                .contains("match_signals jsonb NOT NULL")
                .contains("CREATE TABLE import_candidate_reviews")
                .contains("decision IN ('CONFIRM_MATCH', 'REJECT_MATCH', 'CREATE_NEW_SONG', 'REJECT_CANDIDATE', 'NEEDS_MORE_INFO')")
                .contains("proposed_duplicate_match_id uuid REFERENCES proposed_duplicate_matches")
                .contains("reviewer varchar(255) NOT NULL");
    }

    @Test
    void adr003MigrationAddsRequiredLookupAndReviewIndexes() throws IOException {
        // Arrange / Act
        String migrationSql = readResource("db/migration/V003__import_candidate_deduplication_schema.sql");

        // Assert
        assertThat(migrationSql)
                .contains("import_candidates_import_batch_id_idx")
                .contains("import_candidates_normalized_title_idx")
                .contains("import_candidates_ccli_number_idx")
                .contains("import_candidates_lyrics_hash_idx")
                .contains("import_candidates_status_idx")
                .contains("import_candidate_reviews_decision_idx")
                .contains("proposed_duplicate_matches_status_idx");
    }

    private static String readResource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}

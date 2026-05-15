package com.cadentia.catalog.model;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogWriteRequestValidationTest {

    @Test
    void createSongRejectsBlankRequiredText() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateSongRequest(
                        " ",
                        "fixture-song",
                        "en",
                        null,
                        null,
                        null,
                        2024,
                        SongStatus.DRAFT,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonicalTitle");
    }

    @Test
    void createSongRejectsInvalidYearBeforeDatabaseWrite() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateSongRequest(
                        "Fixture Song",
                        "fixture-song",
                        "en",
                        null,
                        null,
                        null,
                        10000,
                        SongStatus.DRAFT,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yearWritten");
    }

    @Test
    void createArrangementRejectsOutOfRangeMusicalMetadata() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateArrangementRequest(
                        UUID.randomUUID(),
                        "Default",
                        "default",
                        ArrangementSourceType.CUSTOM,
                        "en",
                        "G",
                        KeyMode.MAJOR,
                        120,
                        "4/4",
                        180,
                        6,
                        3,
                        true,
                        true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("energyLevel");
    }

    @Test
    void createLyricsDocumentRejectsNonPositiveVersion() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateLyricsDocumentRequest(
                        UUID.randomUUID(),
                        LyricsFormat.PLAIN_TEXT,
                        "Fixture lyrics excerpt",
                        "sha256:fixture",
                        0,
                        true,
                        false,
                        false,
                        "fixture://lyrics",
                        "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versionNumber");
    }

    @Test
    void createProvenanceRecordRejectsMultipleEntityTargets() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateProvenanceRecordRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        "fixture",
                        "fixture://source",
                        "Fixture source",
                        LicenseType.NOT_APPLICABLE,
                        null,
                        ImportMethod.TEST_FIXTURE,
                        BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void createProvenanceRecordRejectsConfidenceScoreOutsideUnitRange() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateProvenanceRecordRequest(
                        UUID.randomUUID(),
                        null,
                        null,
                        UUID.randomUUID(),
                        "fixture",
                        "fixture://source",
                        "Fixture source",
                        LicenseType.NOT_APPLICABLE,
                        null,
                        ImportMethod.TEST_FIXTURE,
                        new BigDecimal("1.1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidenceScore");
    }

    @Test
    void createApprovalRecordRequiresReviewer() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateApprovalRecordRequest(
                        UUID.randomUUID(),
                        null,
                        null,
                        ApprovalType.DOCTRINAL,
                        ApprovalStatus.PENDING,
                        " ",
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reviewer");
    }

    @Test
    void validFixtureFlowRequestsCanBeConstructedWithoutAuditFields() {
        // Arrange
        UUID songId = UUID.randomUUID();
        UUID arrangementId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();

        // Act / Assert
        assertThatNoException().isThrownBy(() -> {
            new CreateSongRequest("Fixture Song", "fixture-song", "en", null, null, null, 2024,
                    SongStatus.DRAFT, "fixture only");
            new CreateArrangementRequest(songId, "Default", "default", ArrangementSourceType.CUSTOM, "en", "G",
                    KeyMode.MAJOR, 120, "4/4", 180, 3, 2, true, true);
            new CreateLyricsDocumentRequest(arrangementId, LyricsFormat.PLAIN_TEXT, "Fixture lyrics excerpt",
                    "sha256:fixture", 1, true, false, false, "fixture://lyrics", "test");
            new CreateTagRequest(TagType.THEME, "Praise", "praise", "Fixture tag", true);
            new CreateImportBatchRequest("fixture", "test", ImportBatchStatus.PENDING, "{}");
            new CreateProvenanceRecordRequest(songId, null, null, importBatchId, "fixture", "fixture://source",
                    "Fixture source", LicenseType.NOT_APPLICABLE, null, ImportMethod.TEST_FIXTURE, BigDecimal.ONE);
            new CreateApprovalRecordRequest(songId, null, null, ApprovalType.CATALOG_INCLUSION,
                    ApprovalStatus.PENDING, "test", "fixture review");
        });
    }
}

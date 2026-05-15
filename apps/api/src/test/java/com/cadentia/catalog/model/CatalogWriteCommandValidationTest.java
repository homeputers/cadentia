package com.cadentia.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogWriteCommandValidationTest {

    @Test
    void createSongRejectsBlankRequiredText() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateSongCommand(
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
        assertThatThrownBy(() -> new CreateSongCommand(
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
        assertThatThrownBy(() -> new CreateArrangementCommand(
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
        assertThatThrownBy(() -> new CreateLyricsDocumentCommand(
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
    void updateLyricsDocumentRejectsBlankEditor() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new UpdateLyricsDocumentCommand(
                        LyricsFormat.PLAIN_TEXT,
                        "Fixture lyrics excerpt",
                        "sha256:fixture",
                        false,
                        false,
                        "fixture://lyrics",
                        " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("editedBy");
    }

    @Test
    void lyricsFormatAcceptsOnlyAdr004DeclaredValues() {
        // Arrange
        String[] supportedFormats = {"plain_text", "chordpro", "onsong", "markdown"};

        // Act / Assert
        for (String supportedFormat : supportedFormats) {
            assertThat(LyricsFormat.fromDeclaredValue(supportedFormat).storageValue()).isEqualTo(supportedFormat);
        }
        assertThat(LyricsFormat.acceptedValues()).isEqualTo("plain_text, chordpro, onsong, markdown");
    }

    @Test
    void lyricsFormatRejectsUnsupportedDeclaredValueWithAcceptedValues() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> LyricsFormat.fromDeclaredValue("openlyrics"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openlyrics")
                .hasMessageContaining("plain_text, chordpro, onsong, markdown");
    }

    @Test
    void createProvenanceRecordRejectsMultipleEntityTargets() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateProvenanceRecordCommand(
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
        assertThatThrownBy(() -> new CreateProvenanceRecordCommand(
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
        assertThatThrownBy(() -> new CreateApprovalRecordCommand(
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
            new CreateSongCommand("Fixture Song", "fixture-song", "en", null, null, null, 2024,
                    SongStatus.DRAFT, "fixture only");
            new CreateArrangementCommand(songId, "Default", "default", ArrangementSourceType.CUSTOM, "en", "G",
                    KeyMode.MAJOR, 120, "4/4", 180, 3, 2, true, true);
            new CreateLyricsDocumentCommand(arrangementId, LyricsFormat.PLAIN_TEXT, "Fixture lyrics excerpt",
                    "sha256:fixture", 1, true, false, false, "fixture://lyrics", "test");
            new UpdateLyricsDocumentCommand(LyricsFormat.MARKDOWN, "## Fixture excerpt",
                    "sha256:fixture-update", false, true, "fixture://lyrics/update", "editor");
            new CreateTagCommand(TagType.THEME, "Praise", "praise", "Fixture tag", true);
            new CreateImportBatchCommand("fixture", "test", ImportBatchStatus.PENDING, "{}");
            new CreateProvenanceRecordCommand(songId, null, null, importBatchId, "fixture", "fixture://source",
                    "Fixture source", LicenseType.NOT_APPLICABLE, null, ImportMethod.TEST_FIXTURE, BigDecimal.ONE);
            new CreateApprovalRecordCommand(songId, null, null, ApprovalType.CATALOG_INCLUSION,
                    ApprovalStatus.PENDING, "test", "fixture review");
        });
    }
}

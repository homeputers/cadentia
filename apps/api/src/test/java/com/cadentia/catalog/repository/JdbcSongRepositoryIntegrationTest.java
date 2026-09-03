package com.cadentia.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ImportBatch;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.ParserRunHistory;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.entity.Tag;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateImportBatchCommand;
import com.cadentia.catalog.model.CreateImportCandidateCommand;
import com.cadentia.catalog.model.CreateImportCandidateReviewCommand;
import com.cadentia.catalog.model.CreateLyricsDocumentCommand;
import com.cadentia.catalog.model.CreateProposedDuplicateMatchCommand;
import com.cadentia.catalog.model.CreateProvenanceRecordCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.CreateTagCommand;
import com.cadentia.catalog.model.DuplicateMatchStatus;
import com.cadentia.catalog.model.ImportBatchStatus;
import com.cadentia.catalog.model.ImportCandidateReviewDecision;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.LyricsParseStatus;
import com.cadentia.catalog.model.SongStatus;
import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.model.UpdateLyricsParseResultCommand;
import com.cadentia.catalog.model.UpdateApprovalRecordCommand;
import com.cadentia.catalog.model.UpdateArrangementCommand;
import com.cadentia.catalog.model.UpdateImportBatchCommand;
import com.cadentia.catalog.model.UpdateLyricsDocumentCommand;
import com.cadentia.catalog.model.UpdateSongCommand;
import com.cadentia.catalog.model.UpdateTagCommand;
import com.cadentia.catalog.service.ArrangementRetrievalResult;
import com.cadentia.catalog.service.ArrangementTranspositionSource;
import com.cadentia.catalog.service.CatalogService;
import com.cadentia.catalog.transposition.MusicalKey;
import com.cadentia.scraperadmin.CatalogSongCandidate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.groups.Tuple;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcSongRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcSongRepository repository;
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
        repository = new JdbcSongRepository(jdbcTemplate);
    }

    @Test
    void databaseRejectsUnsupportedLyricsFormat() {
        // Arrange
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);

        // Act / Assert
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO lyrics_documents (
                    arrangement_id, format, content, content_hash, version_number, is_current,
                    contains_chords, contains_sections, source_reference, created_by
                ) VALUES (
                    :arrangementId, 'openlyrics', 'Fixture lyrics excerpt', 'unsupported-format-hash', 1, true,
                    false, false, 'fixture://lyrics', 'integration-test'
                )
                """, Map.of("arrangementId", arrangement.id())))
                .hasMessageContaining("lyrics_documents_format_valid");
    }

    @Test
    void executesImportBatchQueriesAgainstPostgres() {
        // Arrange / Act
        ImportBatch importBatch = createImportBatch();
        ImportBatch completedBatch = repository.updateImportBatch(importBatch.id(), new UpdateImportBatchCommand(
                ImportBatchStatus.COMPLETED, "{\"acceptedCandidates\":1}", true)).orElseThrow();

        // Assert
        assertThat(completedBatch.status()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(completedBatch.completedAt()).isNotNull();
        assertThat(repository.findImportBatchById(importBatch.id())).contains(completedBatch);
    }

    @Test
    void executesSongQueriesAgainstPostgres() {
        // Arrange / Act
        Song song = createSong();
        Song updatedSong = repository.updateSong(song.id(), new UpdateSongCommand(
                song.canonicalTitle(),
                song.normalizedTitle(),
                song.primaryLanguage(),
                song.originalArtistDisplay(),
                song.composerCredits(),
                song.ccliNumber(),
                song.yearWritten(),
                SongStatus.IN_REVIEW,
                "Updated doctrinal notes.")).orElseThrow();

        // Assert
        assertThat(updatedSong.songStatus()).isEqualTo(SongStatus.IN_REVIEW);
        assertThat(repository.findById(song.id())).contains(updatedSong);
        assertThat(repository.findByNormalizedTitleAndLanguage(song.normalizedTitle(), song.primaryLanguage()))
                .contains(updatedSong);
    }

    @Test
    void executesArrangementLyricsAndTagQueriesAgainstPostgres() {
        // Arrange / Act
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        Arrangement updatedArrangement = repository.updateArrangement(arrangement.id(), new UpdateArrangementCommand(
                arrangement.name(),
                arrangement.normalizedName(),
                ArrangementSourceType.CUSTOM,
                arrangement.language(),
                "A",
                KeyMode.MAJOR,
                98,
                arrangement.timeSignature(),
                arrangement.durationSeconds(),
                arrangement.energyLevel(),
                arrangement.difficultyLevel(),
                true,
                true)).orElseThrow();
        LyricsDocument lyricsDocument = createLyricsDocument(arrangement);
        Tag tag = repository.createTag(new CreateTagCommand(
                TagType.THEME, "Faithfulness", "faithfulness", "Fixture taxonomy tag", true));
        Tag updatedTag = repository.updateTag(tag.id(), new UpdateTagCommand(
                "Faithfulness Updated", "faithfulness-updated", "Updated fixture taxonomy tag", true)).orElseThrow();
        assertThat(repository.addTagToSong(song.id(), updatedTag.id())).isTrue();
        assertThat(repository.addTagToArrangement(arrangement.id(), updatedTag.id())).isTrue();
        assertThat(repository.addTagToLyricsDocument(lyricsDocument.id(), updatedTag.id())).isTrue();

        // Assert
        assertThat(repository.findArrangementById(arrangement.id())).contains(updatedArrangement);
        assertThat(repository.findArrangementsBySongId(song.id())).containsExactly(updatedArrangement);
        assertThat(repository.findLyricsDocumentById(lyricsDocument.id())).contains(lyricsDocument);
        assertThat(repository.findLyricsDocumentsByArrangementId(arrangement.id())).containsExactly(lyricsDocument);
        assertThat(repository.findTagById(updatedTag.id())).contains(updatedTag);
        assertThat(repository.findTagByTypeAndSlug(updatedTag.tagType(), updatedTag.slug())).contains(updatedTag);
        assertThat(repository.findTagsBySongId(song.id())).containsExactly(updatedTag);
        assertThat(repository.findTagsByArrangementId(arrangement.id())).containsExactly(updatedTag);
        assertThat(repository.findTagsByLyricsDocumentId(lyricsDocument.id())).containsExactly(updatedTag);
    }

    @Test
    void removeTagFromSongDeletesOnlyTheRequestedAssignment() {
        // Arrange
        Song song = createSong();
        Tag removed = repository.createTag(new CreateTagCommand(
                TagType.THEME, "Repentance", "repentance", "Fixture taxonomy tag", true));
        Tag kept = repository.createTag(new CreateTagCommand(
                TagType.MOOD, "Reflective Removal Guard", "reflective-removal-guard", "Fixture taxonomy tag", true));
        repository.addTagToSong(song.id(), removed.id());
        repository.addTagToSong(song.id(), kept.id());

        // Act / Assert
        assertThat(repository.removeTagFromSong(song.id(), removed.id())).isTrue();
        assertThat(repository.removeTagFromSong(song.id(), removed.id())).isFalse();
        assertThat(repository.findTagsBySongId(song.id())).containsExactly(kept);
        assertThat(repository.findTagById(removed.id())).contains(removed);
    }

    @Test
    void tagAssignmentWritesReportDuplicateMappingsWithoutCreatingExtraRows() {
        // Arrange
        Song song = createSong();
        Tag tag = repository.createTag(new CreateTagCommand(
                TagType.MOOD,
                "Joyful Duplicate Guard",
                "joyful-duplicate-guard",
                "Fixture taxonomy tag",
                true));

        // Act / Assert
        assertThat(repository.addTagToSong(song.id(), tag.id())).isTrue();
        assertThat(repository.addTagToSong(song.id(), tag.id())).isFalse();
        assertThat(repository.findTagsBySongId(song.id())).containsExactly(tag);
    }

    @Test
    void databaseRejectsDuplicateCanonicalTagNamesWithinSameType() {
        // Arrange
        repository.createTag(new CreateTagCommand(
                TagType.MOOD,
                "Reflective",
                "reflective",
                "Fixture taxonomy tag",
                true));

        // Act / Assert
        assertThatThrownBy(() -> repository.createTag(new CreateTagCommand(
                        TagType.MOOD,
                        "reflective",
                        "reflective-alias",
                        "Duplicate canonical name",
                        true)))
                .hasMessageContaining("tags_tag_type_lower_name_unique_idx");
    }

    @Test
    void migrationsSeedInitialControlledVocabularyForEveryTagType() {
        // Arrange / Act
        List<Map<String, Object>> seededTags = jdbcTemplate.queryForList("""
                SELECT tag_type, name, slug, sort_order, is_active
                FROM tags
                WHERE id::text LIKE '0f0d9f53-9347-4d7e-a0a0-916571f6f00%'
                ORDER BY tag_type, sort_order, slug
                """, Map.of());

        // Assert
        assertThat(seededTags)
                .extracting(
                        row -> row.get("tag_type"),
                        row -> row.get("name"),
                        row -> row.get("slug"),
                        row -> row.get("is_active"))
                .containsExactlyInAnyOrder(
                        Tuple.tuple("THEME", "Gratitude", "theme-gratitude", true),
                        Tuple.tuple("MOOD", "Celebratory", "mood-celebratory", true),
                        Tuple.tuple("OCCASION", "Gathering", "occasion-gathering", true),
                        Tuple.tuple("SCRIPTURE", "Psalms", "scripture-psalms", true),
                        Tuple.tuple("SEASON", "Year Round", "season-year-round", true),
                        Tuple.tuple("MUSICAL_STYLE", "Contemporary", "musical-style-contemporary", true),
                        Tuple.tuple("AUDIENCE", "Congregation", "audience-congregation", true));
    }

    @Test
    void databaseRejectsUnsupportedTagTypes() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO tags (tag_type, name, slug, description, is_active)
                VALUES ('TOPIC', 'Unsupported', 'unsupported', 'Unsupported legacy type', true)
                """, Map.of()))
                .hasMessageContaining("tags_tag_type_valid");
    }

    @Test
    void retrievesDynamicArrangementTranspositionsWithoutCreatingArrangementRows() {
        // Arrange
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        String rawContent = "[G]Alpha [C]Beta [D/F#]Gamma";
        LyricsDocument lyricsDocument = repository.createLyricsDocument(new CreateLyricsDocumentCommand(
                arrangement.id(), LyricsFormat.CHORDPRO, rawContent, "dynamic-transposition-hash", 1, true,
                true, true, "fixture://lyrics/dynamic-transposition", "integration-test"));
        repository.updateLyricsParseResult(lyricsDocument.id(),
                new UpdateLyricsParseResultCommand(
                        LyricsParseStatus.PARSED,
                        null,
                        "deterministic-chordpro-parser",
                        "adr-004-v1",
                        "[{\"label\":\"verse\",\"startLine\":1,\"endLine\":1}]",
                        "[{\"chord\":\"G\",\"line\":1,\"characterOffset\":0},"
                                + "{\"chord\":\"C\",\"line\":1,\"characterOffset\":8},"
                                + "{\"chord\":\"D/F#\",\"line\":1,\"characterOffset\":16}]",
                        "[]"))
                .orElseThrow();
        CatalogService service = new CatalogService(repository);
        Integer initialArrangementCount = arrangementCount();

        // Act
        ArrangementRetrievalResult aResult = service.retrieveArrangement(
                arrangement.id(), Optional.of(new MusicalKey("A", KeyMode.MAJOR))).orElseThrow();
        ArrangementRetrievalResult bbResult = service.retrieveArrangement(
                arrangement.id(), Optional.of(new MusicalKey("Bb", KeyMode.MAJOR))).orElseThrow();

        // Assert
        assertThat(aResult.baseKey()).isEqualTo(new MusicalKey("G", KeyMode.MAJOR));
        assertThat(aResult.requestedTargetKey()).isEqualTo(new MusicalKey("A", KeyMode.MAJOR));
        assertThat(aResult.transpositionInterval()).isEqualTo(2);
        assertThat(aResult.transpositionSource()).isEqualTo(ArrangementTranspositionSource.PARSED_CHORD_MAP);
        assertThat(aResult.chordMapJson()).contains("\"chord\":\"A\"").contains("\"chord\":\"D\"");
        assertThat(bbResult.requestedTargetKey()).isEqualTo(new MusicalKey("Bb", KeyMode.MAJOR));
        assertThat(bbResult.transpositionInterval()).isEqualTo(3);
        assertThat(bbResult.chordMapJson()).contains("\"chord\":\"Bb\"").contains("\"chord\":\"Eb\"");
        assertThat(repository.findLyricsDocumentById(lyricsDocument.id()).orElseThrow().content())
                .isEqualTo(rawContent);
        assertThat(repository.findArrangementById(arrangement.id()).orElseThrow().musicalKey()).isEqualTo("G");
        assertThat(arrangementCount()).isEqualTo(initialArrangementCount);
    }

    @Test
    void storesRawLyricsContentWithoutLossyNormalization() {
        // Arrange
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        String chordProContent = "{title: Fixture}\n{start_of_verse}\n[A]Alpha [D/F#]Beta\n{end_of_verse}\n";
        String markdownContent = "## Verse 1\n- Alpha **beta**\n\n> spoken marker\n";
        String plainTextContent = "Line one\n\n  Line two with leading spaces\nLine three  ";

        // Act
        LyricsDocument chordProDocument = repository.createLyricsDocument(new CreateLyricsDocumentCommand(
                arrangement.id(), LyricsFormat.CHORDPRO, chordProContent, "raw-chordpro-hash", 1, true,
                true, true, "fixture://lyrics/chordpro", "integration-test"));
        LyricsDocument markdownDocument = repository.createLyricsDocument(new CreateLyricsDocumentCommand(
                arrangement.id(), LyricsFormat.MARKDOWN, markdownContent, "raw-markdown-hash", 2, true,
                false, true, "fixture://lyrics/markdown", "integration-test"));
        LyricsDocument plainTextDocument = repository.createLyricsDocument(new CreateLyricsDocumentCommand(
                arrangement.id(), LyricsFormat.PLAIN_TEXT, plainTextContent, "raw-plain-text-hash", 3, true,
                false, false, "fixture://lyrics/plain-text", "integration-test"));

        // Assert
        assertThat(repository.findLyricsDocumentById(chordProDocument.id()).orElseThrow().content())
                .isEqualTo(chordProContent);
        assertThat(repository.findLyricsDocumentById(markdownDocument.id()).orElseThrow().content())
                .isEqualTo(markdownContent);
        assertThat(repository.findLyricsDocumentById(plainTextDocument.id()).orElseThrow().content())
                .isEqualTo(plainTextContent);
        assertThat(repository.findLyricsDocumentsByArrangementId(arrangement.id()))
                .extracting(LyricsDocument::current)
                .containsExactly(false, false, true);
    }


    @Test
    void storesDerivedParseResultsWithoutMutatingRawLyricsContent() {
        // Arrange
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        String rawContent = """
                {start_of_chorus}
                [G]Alpha fixture line
                {end_of_chorus}""";
        LyricsDocument lyricsDocument = repository.createLyricsDocument(new CreateLyricsDocumentCommand(
                arrangement.id(), LyricsFormat.CHORDPRO, rawContent, "parse-storage-hash", 1, true,
                true, true, "fixture://lyrics/parse-storage", "integration-test"));

        // Act
        LyricsDocument parsedDocument = repository.updateLyricsParseResult(lyricsDocument.id(),
                new UpdateLyricsParseResultCommand(
                        LyricsParseStatus.PARSED,
                        null,
                        "deterministic-chordpro-parser",
                        "adr-004-v1",
                        """
                                [{"label":"chorus","startLine":1,"endLine":3,"lines":["[G]Alpha fixture line"]}]""",
                        """
                                [{"chord":"G","line":2,"characterOffset":0}]""",
                        """
                                [{"type":"section_start","label":"chorus","line":1}]"""))
                .orElseThrow();

        // Assert
        assertThat(parsedDocument.content()).isEqualTo(rawContent);
        assertThat(parsedDocument.parseStatus()).isEqualTo(LyricsParseStatus.PARSED);
        assertThat(parsedDocument.parsedAt()).isNotNull();
        assertThat(parsedDocument.parsedSectionsJson()).contains("chorus");
        assertThat(repository.findLyricsDocumentById(lyricsDocument.id()).orElseThrow().content())
                .isEqualTo(rawContent);
    }

    @Test
    void appendsParserRunHistoryAndReadsLineageInBothDirections() {
        // Arrange
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        LyricsDocument lyricsDocument = createLyricsDocument(arrangement);
        ParserRunHistory firstRun = new ParserRunHistory(
                java.util.UUID.randomUUID(),
                lyricsDocument.id(),
                "plain_text",
                "1.0.0",
                lyricsDocument.contentHash(),
                "IMPORT",
                "system:ingestion",
                null,
                null,
                null,
                LyricsParseStatus.PARSED,
                List.of("ambiguous_token"),
                List.of("chords:0.81"));

        // Act
        ParserRunHistory insertedFirst = repository.appendParserRunHistory(firstRun);
        ParserRunHistory secondRun = new ParserRunHistory(
                java.util.UUID.randomUUID(),
                lyricsDocument.id(),
                "plain_text",
                "1.1.0",
                lyricsDocument.contentHash(),
                "RECALCULATION",
                "system:lyrics-parser",
                null,
                insertedFirst.id(),
                null,
                LyricsParseStatus.PARSED,
                List.of("section_inference"),
                List.of("sections:0.92"));
        ParserRunHistory insertedSecond = repository.appendParserRunHistory(secondRun);

        // Assert
        assertThat(insertedFirst.createdAt()).isNotNull();
        assertThat(insertedSecond.supersedesRunId()).isEqualTo(insertedFirst.id());
        assertThat(repository.findLatestParserRunHistoryByLyricsDocumentId(lyricsDocument.id()))
                .contains(insertedSecond);
        assertThat(repository.findParserRunHistoryByLyricsDocumentId(lyricsDocument.id()))
                .extracting(
                        ParserRunHistory::id,
                        ParserRunHistory::supersedesRunId,
                        ParserRunHistory::supersededByRunId,
                        ParserRunHistory::warnings,
                        ParserRunHistory::confidenceSnapshot)
                .containsExactly(
                        Tuple.tuple(
                                insertedFirst.id(),
                                null,
                                insertedSecond.id(),
                                List.of("ambiguous_token"),
                                List.of("chords:0.81")),
                        Tuple.tuple(
                                insertedSecond.id(),
                                insertedFirst.id(),
                                null,
                                List.of("section_inference"),
                                List.of("sections:0.92")));
    }

    @Test
    void recordsParseFailuresWithoutBlockingRawDocumentStorage() {
        // Arrange
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        LyricsDocument lyricsDocument = repository.createLyricsDocument(new CreateLyricsDocumentCommand(
                arrangement.id(), LyricsFormat.ONSONG, "Fixture OnSong excerpt", "unsupported-parser-hash", 1, true,
                false, false, "fixture://lyrics/onsong", "integration-test"));

        // Act
        LyricsDocument unsupportedDocument = repository.updateLyricsParseResult(lyricsDocument.id(),
                new UpdateLyricsParseResultCommand(
                        LyricsParseStatus.UNSUPPORTED,
                        "No deterministic parser is currently implemented for format onsong",
                        null,
                        null,
                        null,
                        null,
                        null))
                .orElseThrow();

        // Assert
        assertThat(unsupportedDocument.content()).isEqualTo("Fixture OnSong excerpt");
        assertThat(unsupportedDocument.parseStatus()).isEqualTo(LyricsParseStatus.UNSUPPORTED);
        assertThat(unsupportedDocument.parseError()).contains("onsong");
        assertThat(unsupportedDocument.parsedSectionsJson()).isNull();
    }

    @Test
    void updatingLyricsDocumentCreatesNewAuditableVersionAndPreservesProvenance() {
        // Arrange
        ImportBatch importBatch = createImportBatch();
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        String initialContent = "Initial fixture line\nwith exact breaks";
        String updatedContent = "# Updated Fixture\n\n[A]Alpha marker remains raw\n";
        LyricsDocument initialDocument = repository.createLyricsDocument(new CreateLyricsDocumentCommand(
                arrangement.id(), LyricsFormat.PLAIN_TEXT, initialContent, "initial-raw-hash", 1, true,
                false, false, "fixture://lyrics/initial", "integration-test"));
        ProvenanceRecord initialProvenance = repository.createProvenanceRecord(new CreateProvenanceRecordCommand(
                null, null, initialDocument.id(), importBatch.id(), "fixture-admin",
                "https://example.test/lyrics/initial", "Initial fixture lyrics source", LicenseType.NOT_APPLICABLE,
                "Fixture-only excerpt for storage tests.", ImportMethod.TEST_FIXTURE, BigDecimal.ONE));

        // Act
        LyricsDocument updatedDocument = repository.updateLyricsDocument(initialDocument.id(),
                new UpdateLyricsDocumentCommand(
                        LyricsFormat.MARKDOWN, updatedContent, "updated-raw-hash", true, true,
                        "fixture://lyrics/updated", "editor@example.test"))
                .orElseThrow();

        // Assert
        assertThat(updatedDocument.versionNumber()).isEqualTo(2);
        assertThat(updatedDocument.current()).isTrue();
        assertThat(updatedDocument.content()).isEqualTo(updatedContent);
        assertThat(updatedDocument.createdBy()).isEqualTo("editor@example.test");
        assertThat(repository.findProvenanceRecordById(initialProvenance.id()).orElseThrow().lyricsDocumentId())
                .isEqualTo(initialDocument.id());
        assertThat(repository.findLyricsDocumentsByArrangementId(arrangement.id()))
                .extracting(LyricsDocument::content, LyricsDocument::versionNumber, LyricsDocument::current)
                .containsExactly(
                        Tuple.tuple(initialContent, 1, false),
                        Tuple.tuple(updatedContent, 2, true));
    }

    @Test
    void executesImportCandidateMatchReviewAndMergeQueriesAgainstPostgres() {
        // Arrange / Act
        ImportBatch importBatch = createImportBatch();
        Song song = createSong();
        ImportCandidate importCandidate = createImportCandidate(importBatch);
        ImportCandidate candidateInReview = repository.updateImportCandidateStatus(
                importCandidate.id(), ImportCandidateStatus.DEDUPLICATION_REVIEW).orElseThrow();
        ProposedDuplicateMatch proposedDuplicateMatch = createProposedDuplicateMatch(importCandidate, song);
        ProposedDuplicateMatch reviewedDuplicateMatch = repository.updateProposedDuplicateMatchStatus(
                proposedDuplicateMatch.id(), DuplicateMatchStatus.REVIEWED).orElseThrow();
        ImportCandidateReview review = repository.createImportCandidateReview(new CreateImportCandidateReviewCommand(
                importCandidate.id(),
                proposedDuplicateMatch.id(),
                ImportCandidateReviewDecision.CONFIRM_MATCH,
                "reviewer@example.test",
                "Confirmed by CCLI and normalized title."));
        ImportCandidate mergedCandidate = repository.markImportCandidateMerged(importCandidate.id(), song.id())
                .orElseThrow();

        // Assert
        assertThat(candidateInReview.status()).isEqualTo(ImportCandidateStatus.DEDUPLICATION_REVIEW);
        assertThat(repository.findImportCandidatesByBatchId(importBatch.id())).containsExactly(mergedCandidate);
        assertThat(repository.findImportCandidateById(importCandidate.id())).contains(mergedCandidate);
        assertThat(repository.findProposedDuplicateMatchesByImportCandidateId(importCandidate.id()))
                .containsExactly(reviewedDuplicateMatch);
        assertThat(repository.findProposedDuplicateMatchById(proposedDuplicateMatch.id()))
                .contains(reviewedDuplicateMatch);
        assertThat(repository.findImportCandidateReviewsByImportCandidateId(importCandidate.id()))
                .containsExactly(review);
        assertThat(mergedCandidate.status()).isEqualTo(ImportCandidateStatus.MERGED);
        assertThat(mergedCandidate.mergedSongId()).isEqualTo(song.id());
    }

    @Test
    void executesProvenanceAndApprovalQueriesAgainstPostgres() {
        // Arrange / Act
        ImportBatch importBatch = createImportBatch();
        Song song = createSong();
        ProvenanceRecord provenanceRecord = createProvenanceRecord(importBatch, song);
        ApprovalRecord approvalRecord = repository.createApprovalRecord(new CreateApprovalRecordCommand(
                song.id(),
                null,
                null,
                ApprovalType.EDITORIAL,
                ApprovalStatus.PENDING,
                "reviewer@example.test",
                "Pending ADR-005 approval workflow."));
        ApprovalRecord updatedApprovalRecord = repository.updateApprovalRecord(
                approvalRecord.id(),
                new UpdateApprovalRecordCommand(
                        ApprovalStatus.APPROVED,
                        "reviewer@example.test",
                        "Approved after ADR-005 review.")).orElseThrow();

        // Assert
        assertThat(repository.findProvenanceRecordById(provenanceRecord.id())).contains(provenanceRecord);
        assertThat(repository.findProvenanceRecordsForSong(song.id())).containsExactly(provenanceRecord);
        assertThat(repository.findApprovalRecordById(approvalRecord.id())).contains(updatedApprovalRecord);
        assertThat(repository.findApprovalRecordsForSong(song.id())).containsExactly(updatedApprovalRecord);
    }

    @Test
    void rejectsDuplicateApprovalRecordForSameEntityAndType() {
        // Arrange
        Song song = createSong();
        repository.createApprovalRecord(new CreateApprovalRecordCommand(
                song.id(),
                null,
                null,
                ApprovalType.DOCTRINAL,
                ApprovalStatus.PENDING,
                "reviewer@example.test",
                "Initial doctrinal review request."));

        // Act / Assert
        assertThatThrownBy(() -> repository.createApprovalRecord(new CreateApprovalRecordCommand(
                        song.id(),
                        null,
                        null,
                        ApprovalType.DOCTRINAL,
                        ApprovalStatus.PENDING,
                        "second-reviewer@example.test",
                        "Duplicate doctrinal review request.")))
                .hasMessageContaining("approval_records_one_song_approval_type_idx");
    }

    @Test
    void rejectsInvalidApprovalStatusTransitionAgainstPostgres() {
        // Arrange
        Song song = createSong();
        ApprovalRecord approvalRecord = repository.createApprovalRecord(new CreateApprovalRecordCommand(
                song.id(),
                null,
                null,
                ApprovalType.LICENSING,
                ApprovalStatus.PENDING,
                "reviewer@example.test",
                "Pending license review."));
        repository.updateApprovalRecord(approvalRecord.id(), new UpdateApprovalRecordCommand(
                ApprovalStatus.APPROVED,
                "reviewer@example.test",
                "License approved.")).orElseThrow();

        // Act / Assert
        assertThatThrownBy(() -> repository.updateApprovalRecord(approvalRecord.id(), new UpdateApprovalRecordCommand(
                        ApprovalStatus.REJECTED,
                        "reviewer@example.test",
                        "Cannot jump directly from approved to rejected.")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void doctrinalRejectionPreventsArrangementRecommendationEligibility() {
        // Arrange
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        LyricsDocument lyricsDocument = createLyricsDocument(arrangement);
        repository.createApprovalRecord(new CreateApprovalRecordCommand(
                song.id(),
                null,
                null,
                ApprovalType.DOCTRINAL,
                ApprovalStatus.APPROVED,
                "pastor@example.test",
                "Song theology approved."));
        repository.createApprovalRecord(new CreateApprovalRecordCommand(
                null,
                null,
                lyricsDocument.id(),
                ApprovalType.DOCTRINAL,
                ApprovalStatus.REJECTED,
                "pastor@example.test",
                "Current lyrics document has doctrinal concerns."));

        // Act / Assert
        assertThat(repository.isArrangementDoctrinallyApprovedForRecommendation(arrangement.id())).isFalse();
    }

    @Test
    void approvedSongAndCurrentLyricsDoctrinalRecordsAllowArrangementRecommendationEligibility() {
        // Arrange
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        LyricsDocument lyricsDocument = createLyricsDocument(arrangement);
        repository.createApprovalRecord(new CreateApprovalRecordCommand(
                song.id(),
                null,
                null,
                ApprovalType.DOCTRINAL,
                ApprovalStatus.APPROVED,
                "pastor@example.test",
                "Song theology approved."));
        repository.createApprovalRecord(new CreateApprovalRecordCommand(
                null,
                null,
                lyricsDocument.id(),
                ApprovalType.DOCTRINAL,
                ApprovalStatus.APPROVED,
                "pastor@example.test",
                "Current lyrics document approved."));

        // Act / Assert
        assertThat(repository.isArrangementDoctrinallyApprovedForRecommendation(arrangement.id())).isTrue();
    }

    @Test
    void executesDeduplicationCandidateReadQueryAgainstPostgres() {
        // Arrange
        Song song = createSong();
        Arrangement arrangement = createArrangement(song);
        createLyricsDocument(arrangement);

        // Act / Assert
        assertThat(repository.findCatalogSongCandidatesForDeduplication())
                .extracting(CatalogSongCandidate::songId)
                .contains(song.id());
    }

    private Integer arrangementCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM arrangements", Map.of(), Integer.class);
    }

    private ImportBatch createImportBatch() {
        return repository.createImportBatch(new CreateImportBatchCommand(
                "fixture-csv", "admin@example.test", ImportBatchStatus.RUNNING, "{}"));
    }

    private Song createSong() {
        return repository.createSong(new CreateSongCommand(
                "Great Is Thy Faithfulness",
                "great-is-thy-faithfulness",
                "en",
                "Fixture Artist",
                "Fixture Writer",
                "18723",
                1923,
                SongStatus.DRAFT,
                "Initial import review needed."));
    }

    private Arrangement createArrangement(Song song) {
        return repository.createArrangement(new CreateArrangementCommand(
                song.id(),
                "Default Arrangement",
                "default-arrangement",
                ArrangementSourceType.UNKNOWN,
                "en",
                "G",
                KeyMode.MAJOR,
                96,
                "4/4",
                240,
                3,
                2,
                true,
                true));
    }

    private LyricsDocument createLyricsDocument(Arrangement arrangement) {
        return repository.createLyricsDocument(new CreateLyricsDocumentCommand(
                arrangement.id(),
                LyricsFormat.PLAIN_TEXT,
                "Fixture lyric line for repository integration test.",
                "fixture-lyrics-hash",
                1,
                true,
                false,
                false,
                "Fixture lyrics source reference",
                "integration-test"));
    }

    private ImportCandidate createImportCandidate(ImportBatch importBatch) {
        return repository.createImportCandidate(new CreateImportCandidateCommand(
                importBatch.id(),
                "row-1",
                "Great Is Thy Faithfulness (Live)",
                "great-is-thy-faithfulness",
                "Fixture Artist",
                "{\"sourceArtistId\":\"artist-1\"}",
                "18723-live",
                "fixture-import-lyrics-hash",
                "{\"title\":\"Great Is Thy Faithfulness (Live)\"}",
                ImportCandidateStatus.STAGED));
    }

    private ProposedDuplicateMatch createProposedDuplicateMatch(ImportCandidate importCandidate, Song song) {
        return repository.createProposedDuplicateMatch(new CreateProposedDuplicateMatchCommand(
                importCandidate.id(),
                song.id(),
                new BigDecimal("0.9500"),
                "{\"ccliNumber\":\"exact\"}",
                DuplicateMatchStatus.PROPOSED,
                "deterministic-song-deduper"));
    }

    private ProvenanceRecord createProvenanceRecord(ImportBatch importBatch, Song song) {
        return repository.createProvenanceRecord(new CreateProvenanceRecordCommand(
                song.id(),
                null,
                null,
                importBatch.id(),
                "fixture-csv",
                "https://example.test/imports/row-1",
                "Fixture CSV row 1",
                LicenseType.UNKNOWN,
                "metadata-only fixture",
                ImportMethod.CSV_IMPORT,
                BigDecimal.ONE));
    }
}

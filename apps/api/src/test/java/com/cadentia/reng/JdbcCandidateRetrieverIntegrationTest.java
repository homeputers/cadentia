package com.cadentia.reng;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.entity.Tag;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateLyricsDocumentCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.CreateTagCommand;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.SongStatus;
import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.repository.JdbcSongRepository;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcCandidateRetrieverIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcCandidateRetriever candidateRetriever;
    private JdbcSongRepository songRepository;

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
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        candidateRetriever = new JdbcCandidateRetriever(jdbcTemplate);
        songRepository = new JdbcSongRepository(jdbcTemplate);
    }

    @Test
    void findCandidatesExcludesArrangementMissingRequiredApprovals() {
        // Arrange
        Arrangement arrangement = createCatalogContent("missing-approval").arrangement();

        // Act
        List<RecommendableArrangement> candidates = candidateRetriever.findCandidates(defaultCriteria());

        // Assert
        assertThat(candidates)
                .extracting(RecommendableArrangement::arrangementId)
                .doesNotContain(arrangement.id());
    }

    @ParameterizedTest
    @EnumSource(ApprovalStatus.class)
    void findCandidatesRequiresEveryApprovalGateToBeApproved(ApprovalStatus doctrinalStatus) {
        // Arrange
        CatalogContent content = createCatalogContent("status-" + doctrinalStatus.name().toLowerCase());
        approveAllRequiredGates(content, doctrinalStatus);

        // Act
        List<RecommendableArrangement> candidates = candidateRetriever.findCandidates(defaultCriteria());

        // Assert
        if (doctrinalStatus == ApprovalStatus.APPROVED) {
            assertThat(candidates)
                    .extracting(RecommendableArrangement::arrangementId)
                    .contains(content.arrangement().id());
        } else {
            assertThat(candidates)
                    .extracting(RecommendableArrangement::arrangementId)
                    .doesNotContain(content.arrangement().id());
        }
    }

    @Test
    void findCandidatesReturnsApprovalGateDetailsAndDeterministicTagsWithoutReviewNotes() {
        // Arrange
        CatalogContent content = createCatalogContent("approved-detail");
        approveAllRequiredGates(content, ApprovalStatus.APPROVED);
        addArrangementTag(content.arrangement(), "theme", "thanksgiving");
        addArrangementTag(content.arrangement(), "liturgical", "gathering");

        // Act
        List<RecommendableArrangement> candidates = candidateRetriever.findCandidates(new CandidateSearchCriteria(
                "en", List.of("G"), 90, 100, List.of("thanksgiving")));

        // Assert
        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.arrangementId()).isEqualTo(content.arrangement().id());
            assertThat(candidate.currentLyricsDocumentId()).isEqualTo(content.lyricsDocument().id());
            assertThat(candidate.musicalKey()).isEqualTo("G");
            assertThat(candidate.keyMode()).isEqualTo(KeyMode.MAJOR);
            assertThat(candidate.tags()).containsExactly("gathering", "thanksgiving");
            assertThat(candidate.approvalGateSummary()).isEqualTo(new ApprovalGateSummary(
                    ApprovalStatus.APPROVED,
                    ApprovalStatus.APPROVED,
                    ApprovalStatus.APPROVED,
                    ApprovalStatus.APPROVED,
                    ApprovalStatus.APPROVED,
                    ApprovalStatus.APPROVED,
                    ApprovalStatus.APPROVED,
                    ApprovalStatus.APPROVED));
        });
    }

    @Test
    void findCandidatesAppliesDeterministicCatalogFilters() {
        // Arrange
        CatalogContent matching = createCatalogContent("filter-match");
        approveAllRequiredGates(matching, ApprovalStatus.APPROVED);
        addArrangementTag(matching.arrangement(), "theme", "joy");
        CatalogContent excluded = createCatalogContent("filter-excluded");
        approveAllRequiredGates(excluded, ApprovalStatus.APPROVED);

        // Act
        List<RecommendableArrangement> candidates = candidateRetriever.findCandidates(new CandidateSearchCriteria(
                "en", List.of("G"), 94, 96, List.of("joy")));

        // Assert
        assertThat(candidates)
                .extracting(RecommendableArrangement::arrangementId)
                .contains(matching.arrangement().id())
                .doesNotContain(excluded.arrangement().id());
    }

    private static CandidateSearchCriteria defaultCriteria() {
        return new CandidateSearchCriteria(null, List.of(), null, null, List.of());
    }

    private CatalogContent createCatalogContent(String slug) {
        Song song = songRepository.createSong(new CreateSongCommand(
                "Fixture Song " + slug,
                "fixture-song-" + slug,
                "en",
                "Fixture Artist",
                "Fixture Writer",
                "CCLI-" + slug,
                2026,
                SongStatus.IN_REVIEW,
                "Doctrinal review notes must not appear in recommendation candidates."));
        Arrangement arrangement = songRepository.createArrangement(new CreateArrangementCommand(
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
        LyricsDocument lyricsDocument = songRepository.createLyricsDocument(new CreateLyricsDocumentCommand(
                arrangement.id(),
                LyricsFormat.PLAIN_TEXT,
                "Fixture lyrics for " + slug,
                "lyrics-hash-" + slug,
                1,
                true,
                false,
                false,
                "fixture://lyrics/" + slug,
                "integration-test"));
        return new CatalogContent(song, arrangement, lyricsDocument);
    }

    private void approveAllRequiredGates(CatalogContent content, ApprovalStatus songDoctrinalStatus) {
        createApproval(content.song().id(), null, null, ApprovalType.DOCTRINAL, songDoctrinalStatus);
        createApproval(content.song().id(), null, null, ApprovalType.EDITORIAL, ApprovalStatus.APPROVED);
        createApproval(content.song().id(), null, null, ApprovalType.LICENSING, ApprovalStatus.APPROVED);
        createApproval(null, content.arrangement().id(), null, ApprovalType.MUSICAL, ApprovalStatus.APPROVED);
        createApproval(null, content.arrangement().id(), null, ApprovalType.EDITORIAL, ApprovalStatus.APPROVED);
        createApproval(null, null, content.lyricsDocument().id(), ApprovalType.DOCTRINAL, ApprovalStatus.APPROVED);
        createApproval(null, null, content.lyricsDocument().id(), ApprovalType.EDITORIAL, ApprovalStatus.APPROVED);
        createApproval(null, null, content.lyricsDocument().id(), ApprovalType.LICENSING, ApprovalStatus.APPROVED);
    }

    private void createApproval(
            UUID songId,
            UUID arrangementId,
            UUID lyricsDocumentId,
            ApprovalType approvalType,
            ApprovalStatus status) {
        songRepository.createApprovalRecord(new CreateApprovalRecordCommand(
                songId,
                arrangementId,
                lyricsDocumentId,
                approvalType,
                status,
                "reviewer@example.test",
                "Private approval note must not be exposed by v_recommendable_arrangements."));
    }

    private void addArrangementTag(Arrangement arrangement, String slugPrefix, String slug) {
        Tag tag = songRepository.createTag(new CreateTagCommand(
                TagType.THEME,
                slugPrefix + " " + slug,
                slug,
                "Fixture tag",
                true));
        songRepository.addTagToArrangement(arrangement.id(), tag.id());
    }

    private record CatalogContent(Song song, Arrangement arrangement, LyricsDocument lyricsDocument) {
    }
}

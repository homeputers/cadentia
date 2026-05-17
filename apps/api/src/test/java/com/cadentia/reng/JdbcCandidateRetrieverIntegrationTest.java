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
    private JdbcTagReportingRepository tagReportingRepository;
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
        tagReportingRepository = new JdbcTagReportingRepository(jdbcTemplate);
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
        addSongTag(content.song(), TagType.THEME, "Adoration", "adoration", true);
        addArrangementTag(content.arrangement(), "theme", "thanksgiving");
        addArrangementTag(content.arrangement(), "liturgical", "gathering");
        addLyricsDocumentTag(content.lyricsDocument(), TagType.THEME, "Grace", "grace", true);
        addArrangementTag(content.arrangement(), "inactive", "aaa-inactive", false);

        // Act
        List<RecommendableArrangement> candidates = candidateRetriever.findCandidates(new CandidateSearchCriteria(
                "en", List.of("G"), 90, 100, List.of(), List.of(TagFilter.bySlug(TagType.THEME, "thanksgiving"))));

        // Assert
        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.arrangementId()).isEqualTo(content.arrangement().id());
            assertThat(candidate.currentLyricsDocumentId()).isEqualTo(content.lyricsDocument().id());
            assertThat(candidate.musicalKey()).isEqualTo("G");
            assertThat(candidate.keyMode()).isEqualTo(KeyMode.MAJOR);
            assertThat(candidate.tags()).containsExactly("adoration", "gathering", "grace", "thanksgiving");
            assertThat(candidate.controlledTags())
                    .extracting(RecommendationTag::tagType, RecommendationTag::slug, RecommendationTag::name)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(TagType.THEME, "adoration", "Adoration"),
                            org.assertj.core.groups.Tuple.tuple(TagType.THEME, "gathering", "liturgical gathering"),
                            org.assertj.core.groups.Tuple.tuple(TagType.THEME, "grace", "Grace"),
                            org.assertj.core.groups.Tuple.tuple(TagType.THEME, "thanksgiving", "theme thanksgiving"));
            assertThat(candidate.matchedTags())
                    .extracting(RecommendationTag::tagType, RecommendationTag::slug, RecommendationTag::name)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(
                            TagType.THEME, "thanksgiving", "theme thanksgiving"));
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
                "en", List.of("G"), 94, 96, List.of(), List.of(TagFilter.bySlug(TagType.THEME, "joy"))));

        // Assert
        assertThat(candidates)
                .extracting(RecommendableArrangement::arrangementId)
                .contains(matching.arrangement().id())
                .doesNotContain(excluded.arrangement().id());
    }

    @Test
    void findCandidatesSupportsEnergyAndApprovalStatusFilters() {
        // Arrange
        CatalogContent lowEnergy = createCatalogContent("energy-low", 2);
        approveAllRequiredGates(lowEnergy, ApprovalStatus.APPROVED);
        CatalogContent matchingEnergy = createCatalogContent("energy-match", 4);
        approveAllRequiredGates(matchingEnergy, ApprovalStatus.APPROVED);

        // Act
        List<RecommendableArrangement> approvedCandidates = candidateRetriever.findCandidates(new CandidateSearchCriteria(
                null,
                List.of(),
                null,
                null,
                4,
                5,
                List.of(),
                List.of(),
                ApprovalStatus.APPROVED));
        List<RecommendableArrangement> pendingCandidates = candidateRetriever.findCandidates(new CandidateSearchCriteria(
                null,
                List.of(),
                null,
                null,
                4,
                5,
                List.of(),
                List.of(),
                ApprovalStatus.PENDING));

        // Assert
        assertThat(approvedCandidates)
                .extracting(RecommendableArrangement::arrangementId)
                .contains(matchingEnergy.arrangement().id())
                .doesNotContain(lowEnergy.arrangement().id());
        assertThat(pendingCandidates)
                .extracting(RecommendableArrangement::arrangementId)
                .doesNotContain(matchingEnergy.arrangement().id(), lowEnergy.arrangement().id());
    }

    @Test
    void findCandidatesSupportsIncludeAnyControlledTagFilters() {
        // Arrange
        CatalogContent joy = createCatalogContent("include-any-joy");
        approveAllRequiredGates(joy, ApprovalStatus.APPROVED);
        addArrangementTag(joy.arrangement(), TagType.THEME, "Joy", "joy", true);
        CatalogContent advent = createCatalogContent("include-any-advent");
        approveAllRequiredGates(advent, ApprovalStatus.APPROVED);
        addArrangementTag(advent.arrangement(), TagType.SEASON, "Advent", "advent", true);
        CatalogContent excluded = createCatalogContent("include-any-excluded");
        approveAllRequiredGates(excluded, ApprovalStatus.APPROVED);

        // Act
        List<RecommendableArrangement> candidates = candidateRetriever.findCandidates(new CandidateSearchCriteria(
                null,
                List.of(),
                null,
                null,
                List.of(TagFilter.bySlug(TagType.THEME, "joy"), TagFilter.bySlug(TagType.SEASON, "advent")),
                List.of()));

        // Assert
        assertThat(candidates)
                .extracting(RecommendableArrangement::arrangementId)
                .contains(joy.arrangement().id(), advent.arrangement().id())
                .doesNotContain(excluded.arrangement().id());
        assertThat(candidates)
                .filteredOn(candidate -> candidate.arrangementId().equals(joy.arrangement().id()))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.matchedTags())
                        .extracting(RecommendationTag::tagType, RecommendationTag::slug)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple(TagType.THEME, "joy")));
    }

    @Test
    void findCandidatesSupportsIncludeAllControlledTagFiltersBySlugAndId() {
        // Arrange
        CatalogContent matching = createCatalogContent("include-all-match");
        approveAllRequiredGates(matching, ApprovalStatus.APPROVED);
        addArrangementTag(matching.arrangement(), TagType.THEME, "Joy", "joy", true);
        Tag advent = addArrangementTag(matching.arrangement(), TagType.SEASON, "Advent", "advent", true);
        CatalogContent missingSeason = createCatalogContent("include-all-missing-season");
        approveAllRequiredGates(missingSeason, ApprovalStatus.APPROVED);
        addArrangementTag(missingSeason.arrangement(), TagType.THEME, "Hope", "hope", true);

        // Act
        List<RecommendableArrangement> candidates = candidateRetriever.findCandidates(new CandidateSearchCriteria(
                null,
                List.of(),
                null,
                null,
                List.of(),
                List.of(TagFilter.bySlug(TagType.THEME, "joy"), TagFilter.byId(TagType.SEASON, advent.id()))));

        // Assert
        assertThat(candidates)
                .extracting(RecommendableArrangement::arrangementId)
                .contains(matching.arrangement().id())
                .doesNotContain(missingSeason.arrangement().id());
        assertThat(candidates).singleElement().satisfies(candidate -> assertThat(candidate.matchedTags())
                .extracting(RecommendationTag::tagType, RecommendationTag::slug)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TagType.SEASON, "advent"),
                        org.assertj.core.groups.Tuple.tuple(TagType.THEME, "joy")));
    }

    @Test
    void tagUsageReportGroupsApprovedRecommendationCandidatesByControlledTag() {
        // Arrange
        CatalogContent first = createCatalogContent("tag-report-first");
        approveAllRequiredGates(first, ApprovalStatus.APPROVED);
        Tag joy = addArrangementTag(first.arrangement(), TagType.THEME, "Joy", "joy", true);
        addArrangementTag(first.arrangement(), TagType.SEASON, "Advent", "advent", true);
        CatalogContent second = createCatalogContent("tag-report-second");
        approveAllRequiredGates(second, ApprovalStatus.APPROVED);
        songRepository.addTagToArrangement(second.arrangement().id(), joy.id());
        CatalogContent unapproved = createCatalogContent("tag-report-unapproved");
        songRepository.addTagToArrangement(unapproved.arrangement().id(), joy.id());

        // Act
        List<TagUsageReportRow> reportRows = tagReportingRepository.findRecommendableArrangementTagUsage();

        // Assert
        assertThat(reportRows)
                .filteredOn(row -> row.tagType() == TagType.THEME && row.slug().equals("joy"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.tagId()).isEqualTo(joy.id());
                    assertThat(row.name()).isEqualTo("Joy");
                    assertThat(row.arrangementCount()).isEqualTo(2);
                });
    }

    private static CandidateSearchCriteria defaultCriteria() {
        return new CandidateSearchCriteria(null, List.of(), null, null, List.of(), List.of());
    }

    private CatalogContent createCatalogContent(String slug) {
        return createCatalogContent(slug, 3);
    }

    private CatalogContent createCatalogContent(String slug, int energyLevel) {
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
                energyLevel,
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

    private Tag addSongTag(Song song, TagType tagType, String name, String slug, boolean active) {
        Tag tag = createTag(tagType, name, slug, active);
        songRepository.addTagToSong(song.id(), tag.id());
        return tag;
    }

    private Tag addLyricsDocumentTag(
            LyricsDocument lyricsDocument, TagType tagType, String name, String slug, boolean active) {
        Tag tag = createTag(tagType, name, slug, active);
        songRepository.addTagToLyricsDocument(lyricsDocument.id(), tag.id());
        return tag;
    }

    private Tag addArrangementTag(Arrangement arrangement, String slugPrefix, String slug) {
        return addArrangementTag(arrangement, slugPrefix, slug, true);
    }

    private Tag addArrangementTag(Arrangement arrangement, String slugPrefix, String slug, boolean active) {
        return addArrangementTag(arrangement, TagType.THEME, slugPrefix + " " + slug, slug, active);
    }

    private Tag addArrangementTag(
            Arrangement arrangement, TagType tagType, String name, String slug, boolean active) {
        Tag tag = createTag(tagType, name, slug, active);
        songRepository.addTagToArrangement(arrangement.id(), tag.id());
        return tag;
    }

    private Tag createTag(TagType tagType, String name, String slug, boolean active) {
        return songRepository.createTag(new CreateTagCommand(
                tagType,
                name,
                slug,
                "Fixture tag",
                active));
    }

    private record CatalogContent(Song song, Arrangement arrangement, LyricsDocument lyricsDocument) {
    }
}

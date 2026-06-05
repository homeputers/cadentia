package com.cadentia.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateLyricsDocumentCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.SongStatus;
import com.cadentia.catalog.repository.JdbcSongRepository;
import com.cadentia.team.ArrangementSuitabilityModels.ArrangementSuitabilityEvaluation;
import com.cadentia.team.ArrangementSuitabilityModels.CoverageRule;
import com.cadentia.team.ArrangementSuitabilityModels.CreateSuitabilityProfileCommand;
import com.cadentia.team.ArrangementSuitabilityModels.CreateSuitabilitySlotCommand;
import com.cadentia.team.ArrangementSuitabilityModels.RequirementType;
import com.cadentia.team.ArrangementSuitabilityModels.SuitabilityFactCategory;
import com.cadentia.team.ArrangementSuitabilityModels.SuitabilityFactStatus;
import com.cadentia.team.ArrangementSuitabilityModels.SuitabilityProfileRecord;
import com.cadentia.team.ArrangementSuitabilityModels.VocalConfiguration;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.ServingPreferenceCode;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import com.cadentia.team.TeamPlanningModels.VocalRangeCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcArrangementSuitabilityRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcArrangementSuitabilityRepository suitabilityRepository;
    private JdbcTeamPlanningRepository teamRepository;
    private JdbcSongRepository songRepository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        suitabilityRepository = new JdbcArrangementSuitabilityRepository(jdbcTemplate);
        teamRepository = new JdbcTeamPlanningRepository(jdbcTemplate, new ObjectMapper());
        songRepository = new JdbcSongRepository(jdbcTemplate);
    }

    @Test
    void persistsVersionedStructuredSuitabilityRequirementsForHumanReview() {
        // Arrange
        CatalogContent content = createCatalogContent("structured-suitability");

        // Act
        SuitabilityProfileRecord profile = suitabilityRepository.createProfile(new CreateSuitabilityProfileCommand(
                content.arrangement().id(),
                1,
                true,
                VocalConfiguration.LEAD_WITH_BACKING,
                55,
                76,
                1,
                "Sparse acoustic team can cover this arrangement.",
                "catalog-governance://ADR-023/review/123",
                "catalog-admin"));
        suitabilityRepository.addSlot(new CreateSuitabilitySlotCommand(
                profile.suitabilityProfileId(),
                RequirementType.REQUIRED,
                MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.ACOUSTIC_GUITAR,
                null,
                SkillLevelCode.INTERMEDIATE,
                1,
                CoverageRule.AT_LEAST,
                "Capo chart reviewed by music director.",
                10));
        suitabilityRepository.addSlot(new CreateSuitabilitySlotCommand(
                profile.suitabilityProfileId(),
                RequirementType.OPTIONAL,
                MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.PERCUSSION,
                null,
                SkillLevelCode.BEGINNER,
                1,
                CoverageRule.AT_LEAST,
                "Cajon is helpful but not required.",
                20));

        // Assert
        assertThat(profile.versionNumber()).isEqualTo(1);
        assertThat(profile.governanceActionRef()).isEqualTo("catalog-governance://ADR-023/review/123");
        assertThat(profile.reviewNotes()).contains("Sparse acoustic");
    }

    @Test
    void evaluatesServiceTeamContextWithPassFailAndWarningSuitabilityFacts() {
        // Arrange
        CatalogContent content = createCatalogContent("evaluation-approved");
        approveAllRequiredGates(content);
        UUID servicePlanId = insertServicePlan();
        MusicianRecord leader = createMusician("Jordan Lead", 50, 79);
        teamRepository.assignVocalPart(leader.musicianId(), VocalPartCode.LEAD, SkillLevelCode.ADVANCED);
        teamRepository.createServiceAssignment(servicePlanId, leader.musicianId(), MusicianRoleCode.VOCALIST,
                null, VocalPartCode.LEAD, AssignmentStatusCode.ACCEPTED, 1, null);
        MusicianRecord guitarist = createMusician("Avery Guitar", null, null);
        teamRepository.assignInstrument(guitarist.musicianId(), InstrumentCode.ACOUSTIC_GUITAR, SkillLevelCode.INTERMEDIATE);
        teamRepository.createServiceAssignment(servicePlanId, guitarist.musicianId(), MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.ACOUSTIC_GUITAR, null, AssignmentStatusCode.ACCEPTED, 2, null);
        SuitabilityProfileRecord profile = createSuitabilityProfile(content.arrangement(), 1);
        addRequiredInstrument(profile, InstrumentCode.ACOUSTIC_GUITAR, SkillLevelCode.INTERMEDIATE);
        addOptionalInstrument(profile, InstrumentCode.PERCUSSION, SkillLevelCode.BEGINNER);

        // Act
        ArrangementSuitabilityEvaluation evaluation = suitabilityRepository.evaluateArrangementForService(
                content.arrangement().id(), servicePlanId);

        // Assert
        assertThat(evaluation.approvalEligible()).isTrue();
        assertThat(evaluation.suitable()).isTrue();
        assertThat(evaluation.facts())
                .extracting("category", "status", "code")
                .contains(
                        org.assertj.core.groups.Tuple.tuple(
                                SuitabilityFactCategory.APPROVAL_GATE,
                                SuitabilityFactStatus.PASS,
                                "ARRANGEMENT_APPROVED_FOR_RECOMMENDATION"),
                        org.assertj.core.groups.Tuple.tuple(
                                SuitabilityFactCategory.SKILL_FLOOR,
                                SuitabilityFactStatus.PASS,
                                "PASS_INSTRUMENT_ACOUSTIC_GUITAR"),
                        org.assertj.core.groups.Tuple.tuple(
                                SuitabilityFactCategory.SKILL_FLOOR,
                                SuitabilityFactStatus.WARNING,
                                "WARNING_INSTRUMENT_PERCUSSION"),
                        org.assertj.core.groups.Tuple.tuple(
                                SuitabilityFactCategory.RANGE,
                                SuitabilityFactStatus.PASS,
                                "LEAD_VOCAL_RANGE"));
    }

    @Test
    void excludesUnapprovedArrangementsEvenWhenTeamFullySatisfiesSuitabilityRequirements() {
        // Arrange
        CatalogContent content = createCatalogContent("evaluation-unapproved");
        UUID servicePlanId = insertServicePlan();
        MusicianRecord leader = createMusician("Taylor Lead", 48, 80);
        teamRepository.assignVocalPart(leader.musicianId(), VocalPartCode.LEAD, SkillLevelCode.ADVANCED);
        teamRepository.createServiceAssignment(servicePlanId, leader.musicianId(), MusicianRoleCode.VOCALIST,
                null, VocalPartCode.LEAD, AssignmentStatusCode.ACCEPTED, 1, null);
        MusicianRecord guitarist = createMusician("Morgan Guitar", null, null);
        teamRepository.assignInstrument(guitarist.musicianId(), InstrumentCode.ACOUSTIC_GUITAR, SkillLevelCode.ADVANCED);
        teamRepository.createServiceAssignment(servicePlanId, guitarist.musicianId(), MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.ACOUSTIC_GUITAR, null, AssignmentStatusCode.ACCEPTED, 2, null);
        SuitabilityProfileRecord profile = createSuitabilityProfile(content.arrangement(), 1);
        addRequiredInstrument(profile, InstrumentCode.ACOUSTIC_GUITAR, SkillLevelCode.INTERMEDIATE);

        // Act
        ArrangementSuitabilityEvaluation evaluation = suitabilityRepository.evaluateArrangementForService(
                content.arrangement().id(), servicePlanId);
        List<UUID> approvedSuitabilityArrangementIds = jdbcTemplate.queryForList(
                "SELECT arrangement_id FROM v_approved_arrangement_suitability_profiles",
                Map.of(),
                UUID.class);

        // Assert
        assertThat(approvedSuitabilityArrangementIds).doesNotContain(content.arrangement().id());
        assertThat(evaluation.approvalEligible()).isFalse();
        assertThat(evaluation.suitable()).isFalse();
        assertThat(evaluation.facts()).singleElement().satisfies(fact -> {
            assertThat(fact.category()).isEqualTo(SuitabilityFactCategory.APPROVAL_GATE);
            assertThat(fact.status()).isEqualTo(SuitabilityFactStatus.FAIL);
            assertThat(fact.code()).isEqualTo("ARRANGEMENT_NOT_APPROVED_FOR_RECOMMENDATION");
        });
    }

    private SuitabilityProfileRecord createSuitabilityProfile(Arrangement arrangement, int versionNumber) {
        return suitabilityRepository.createProfile(new CreateSuitabilityProfileCommand(
                arrangement.id(),
                versionNumber,
                true,
                VocalConfiguration.LEAD_WITH_BACKING,
                52,
                76,
                0,
                "Planning-only suitability note.",
                "catalog-governance://ADR-023/review/" + versionNumber,
                "catalog-admin"));
    }

    private void addRequiredInstrument(
            SuitabilityProfileRecord profile, InstrumentCode instrumentCode, SkillLevelCode minimumSkillLevelCode) {
        suitabilityRepository.addSlot(new CreateSuitabilitySlotCommand(
                profile.suitabilityProfileId(),
                RequirementType.REQUIRED,
                MusicianRoleCode.INSTRUMENTALIST,
                instrumentCode,
                null,
                minimumSkillLevelCode,
                1,
                CoverageRule.AT_LEAST,
                "Structured required instrument.",
                10));
    }

    private void addOptionalInstrument(
            SuitabilityProfileRecord profile, InstrumentCode instrumentCode, SkillLevelCode minimumSkillLevelCode) {
        suitabilityRepository.addSlot(new CreateSuitabilitySlotCommand(
                profile.suitabilityProfileId(),
                RequirementType.OPTIONAL,
                MusicianRoleCode.INSTRUMENTALIST,
                instrumentCode,
                null,
                minimumSkillLevelCode,
                1,
                CoverageRule.AT_LEAST,
                "Structured optional instrument.",
                20));
    }

    private MusicianRecord createMusician(String displayName, Integer comfortableLowMidiNote, Integer comfortableHighMidiNote) {
        return teamRepository.createMusician(new CreateMusicianCommand(
                displayName,
                null,
                null,
                null,
                VocalRangeCode.UNKNOWN,
                comfortableLowMidiNote,
                comfortableHighMidiNote,
                ServingPreferenceCode.AVAILABLE,
                "team-admin"));
    }

    private UUID insertServicePlan() {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO service_plans (service_date_time, title, theme, scripture, notes)
                VALUES (:serviceDateTime, 'Sunday Service', 'Faithfulness', 'Psalm 100', '')
                RETURNING id
                """,
                Map.of("serviceDateTime", Timestamp.from(Instant.parse("2026-06-07T10:00:00Z"))),
                UUID.class);
    }

    private CatalogContent createCatalogContent(String slug) {
        Song song = songRepository.createSong(new CreateSongCommand(
                "Fixture Song " + slug,
                "fixture-song-" + slug,
                "en",
                "Fixture Artist",
                "Fixture Writer",
                "CCLI-SUIT-" + slug,
                2026,
                SongStatus.IN_REVIEW,
                "Doctrinal review is independent from team suitability."));
        Arrangement arrangement = songRepository.createArrangement(new CreateArrangementCommand(
                song.id(),
                "Default Arrangement",
                "default-arrangement-" + slug,
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
                "suitability-lyrics-hash-" + slug,
                1,
                true,
                false,
                false,
                "fixture://lyrics/" + slug,
                "integration-test"));
        return new CatalogContent(song, arrangement, lyricsDocument);
    }

    private void approveAllRequiredGates(CatalogContent content) {
        createApproval(content.song().id(), null, null, ApprovalType.DOCTRINAL);
        createApproval(content.song().id(), null, null, ApprovalType.EDITORIAL);
        createApproval(content.song().id(), null, null, ApprovalType.LICENSING);
        createApproval(null, content.arrangement().id(), null, ApprovalType.MUSICAL);
        createApproval(null, content.arrangement().id(), null, ApprovalType.EDITORIAL);
        createApproval(null, null, content.lyricsDocument().id(), ApprovalType.DOCTRINAL);
        createApproval(null, null, content.lyricsDocument().id(), ApprovalType.EDITORIAL);
        createApproval(null, null, content.lyricsDocument().id(), ApprovalType.LICENSING);
    }

    private void createApproval(UUID songId, UUID arrangementId, UUID lyricsDocumentId, ApprovalType approvalType) {
        songRepository.createApprovalRecord(new CreateApprovalRecordCommand(
                songId,
                arrangementId,
                lyricsDocumentId,
                approvalType,
                ApprovalStatus.APPROVED,
                "reviewer@example.test",
                "Private approval note."));
    }

    private record CatalogContent(Song song, Arrangement arrangement, LyricsDocument lyricsDocument) {
    }
}

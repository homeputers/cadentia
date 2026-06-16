package com.cadentia.asset;

import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.WORSHIP_TEAM;
import static com.cadentia.asset.AssetModels.AssetAttachmentPurposeCode.EVIDENCE;
import static com.cadentia.asset.AssetModels.AssetAttachmentPurposeCode.LOCAL_OVERRIDE;
import static com.cadentia.asset.AssetModels.AssetAttachmentPurposeCode.PRIMARY_CHART;
import static com.cadentia.asset.AssetModels.AssetAttachmentPurposeCode.REHEARSAL;
import static com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode.ARRANGEMENT;
import static com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode.REHEARSAL_SESSION;
import static com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode.SERVICE;
import static com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode.SERVICE_ARRANGEMENT_OVERRIDE;
import static com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode.SONG;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.ARCHIVED;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.CCLI_COVERED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.READY;
import static com.cadentia.asset.AssetModels.AssetTypeCode.CHORD_CHART;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.asset.AssetModels.ArchiveAssetAttachmentCommand;
import com.cadentia.asset.AssetModels.CreateAssetAttachmentCommand;
import com.cadentia.asset.AssetModels.CreateAssetCommand;
import com.cadentia.asset.AssetModels.CreateAssetVersionCommand;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import com.cadentia.asset.AssetModels.ReorderAssetAttachmentCommand;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAssetAttachmentRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcAssetRepository assetRepository;
    private JdbcAssetAttachmentRepository attachmentRepository;
    private NamedParameterJdbcTemplate jdbcTemplate;
    private UUID songId;
    private UUID arrangementId;
    private UUID servicePlanId;
    private UUID serviceItemId;
    private UUID rehearsalSessionId;
    private UUID overrideId;
    private UUID versionOneId;
    private UUID versionTwoId;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        assetRepository = new JdbcAssetRepository(jdbcTemplate);
        attachmentRepository = new JdbcAssetAttachmentRepository(jdbcTemplate);
        seedTargets();
        seedAssetVersions();
    }

    @Test
    void createsCatalogSongAndArrangementAttachments() {
        // Arrange / Act
        var songAttachment = attachmentRepository.createAttachment(command(SONG, songId, null, PRIMARY_CHART, 1));
        var arrangementAttachment = attachmentRepository.createAttachment(command(ARRANGEMENT, arrangementId, null, PRIMARY_CHART, 1));

        // Assert
        assertThat(songAttachment.assetVersionId()).isEqualTo(versionOneId);
        assertThat(arrangementAttachment.targetId()).isEqualTo(arrangementId);
        assertThat(attachmentRepository.listAuditEvents(songAttachment.id()))
                .extracting(event -> event.eventType().name())
                .containsExactly("CREATED");
    }

    @Test
    void serviceAttachmentPinsExactVersionIndependentOfCurrentCatalogAsset() {
        // Arrange
        var attachment = attachmentRepository.createAttachment(command(SERVICE, servicePlanId, servicePlanId, PRIMARY_CHART, 1));
        UUID currentVersion = jdbcTemplate.queryForObject(
                "SELECT current_asset_version_id FROM logical_assets WHERE id = (SELECT asset_id FROM asset_versions WHERE id = :versionId)",
                Map.of("versionId", versionOneId),
                UUID.class);

        // Act / Assert
        assertThat(currentVersion).isEqualTo(versionTwoId);
        assertThat(attachment.assetVersionId()).isEqualTo(versionOneId);
        assertThat(attachmentRepository.listAttachments(SERVICE, servicePlanId))
                .singleElement()
                .satisfies(row -> assertThat(row.assetVersionId()).isEqualTo(versionOneId));
    }

    @Test
    void createsRehearsalSessionAndOverrideAttachmentsWithoutMutatingCatalogArrangement() {
        // Arrange / Act
        var rehearsal = attachmentRepository.createAttachment(
                command(REHEARSAL_SESSION, rehearsalSessionId, servicePlanId, REHEARSAL, 1));
        var override = attachmentRepository.createAttachment(
                command(SERVICE_ARRANGEMENT_OVERRIDE, overrideId, servicePlanId, LOCAL_OVERRIDE, 1));

        // Assert
        assertThat(rehearsal.requiredForUse()).isTrue();
        assertThat(override.targetId()).isEqualTo(overrideId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM arrangements WHERE id = :arrangementId AND name = 'Album Arrangement'",
                Map.of("arrangementId", arrangementId),
                Integer.class)).isOne();
    }

    @Test
    void reordersAndArchivesWithAuditTrail() {
        // Arrange
        var attachment = attachmentRepository.createAttachment(command(SONG, songId, null, PRIMARY_CHART, 1));

        // Act
        var reordered = attachmentRepository.reorderAttachment(
                new ReorderAssetAttachmentCommand(attachment.id(), 2, "planner@cadentia.test", "Move below lead sheet"));
        var archived = attachmentRepository.archiveAttachment(
                new ArchiveAssetAttachmentCommand(attachment.id(), "planner@cadentia.test", "No longer used"));

        // Assert
        assertThat(reordered.sortOrder()).isEqualTo(2);
        assertThat(archived.archivedAt()).isNotNull();
        assertThat(attachmentRepository.listAuditEvents(attachment.id()))
                .extracting(event -> event.eventType().name())
                .containsExactly("CREATED", "REORDERED", "ARCHIVED");
    }

    @Test
    void rejectsInvalidTargetsDuplicateActivePositionsAndArchivedVersions() {
        // Arrange
        attachmentRepository.createAttachment(command(SONG, songId, null, PRIMARY_CHART, 1));
        UUID archivedVersionId = assetRepository.createVersion(version(3, ARCHIVED, false)).id();

        // Act / Assert
        assertThatThrownBy(() -> attachmentRepository.createAttachment(command(SONG, UUID.randomUUID(), null, PRIMARY_CHART, 2)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> attachmentRepository.createAttachment(command(SONG, songId, null, PRIMARY_CHART, 1)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> attachmentRepository.createAttachment(new CreateAssetAttachmentCommand(
                        SONG,
                        songId,
                        null,
                        archivedVersionId,
                        CHORD_CHART,
                        "Archived chart",
                        3,
                        EVIDENCE,
                        false,
                        null,
                        null,
                        WORSHIP_TEAM,
                        "planner@cadentia.test")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> attachmentRepository.createAttachment(command(SERVICE, serviceItemId, servicePlanId, PRIMARY_CHART, 2)))
                .isInstanceOf(DataAccessException.class);
    }

    private void seedTargets() {
        songId = jdbcTemplate.queryForObject(
                """
                INSERT INTO songs (canonical_title, normalized_title, primary_language, song_status)
                VALUES ('Fixture Song', 'fixture song', 'en', 'APPROVED')
                RETURNING id
                """,
                Map.of(),
                UUID.class);
        arrangementId = jdbcTemplate.queryForObject(
                """
                INSERT INTO arrangements (song_id, name, normalized_name, source_type, language, musical_key, key_mode)
                VALUES (:songId, 'Album Arrangement', 'album arrangement', 'STUDIO', 'en', 'G', 'MAJOR')
                RETURNING id
                """,
                Map.of("songId", songId),
                UUID.class);
        servicePlanId = jdbcTemplate.queryForObject(
                """
                INSERT INTO service_plans (service_date_time, title, theme, scripture, notes)
                VALUES ('2026-07-05T15:00:00Z', 'Sunday Service', 'Grace', 'Ephesians 2', 'Fixture notes')
                RETURNING id
                """,
                Map.of(),
                UUID.class);
        serviceItemId = jdbcTemplate.queryForObject(
                """
                INSERT INTO service_plan_blocks (service_plan_id, block_type, position_index, arrangement_id)
                VALUES (:servicePlanId, 'praise', 1, :arrangementId)
                RETURNING id
                """,
                Map.of("servicePlanId", servicePlanId, "arrangementId", arrangementId),
                UUID.class);
        rehearsalSessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO rehearsal_sessions (
                    service_plan_id, session_code, starts_at, ends_at, location, created_by, updated_by
                ) VALUES (
                    :servicePlanId, 'MAIN', '2026-07-03T23:00:00Z', '2026-07-04T01:00:00Z',
                    'Room A', 'planner@cadentia.test', 'planner@cadentia.test'
                )
                RETURNING id
                """,
                Map.of("servicePlanId", servicePlanId),
                UUID.class);
        overrideId = jdbcTemplate.queryForObject(
                """
                INSERT INTO service_arrangement_overrides (
                    service_plan_id, service_plan_block_id, source_arrangement_id, source_arrangement_version_ref,
                    effective_key, rationale, provenance_note, created_by, updated_by
                ) VALUES (
                    :servicePlanId, :serviceItemId, :arrangementId, 'catalog-v1', 'A',
                    'Singer range', 'Planner-selected for this service only', 'planner@cadentia.test', 'planner@cadentia.test'
                )
                RETURNING id
                """,
                Map.of("servicePlanId", servicePlanId, "serviceItemId", serviceItemId, "arrangementId", arrangementId),
                UUID.class);
    }

    private void seedAssetVersions() {
        var asset = assetRepository.createAsset(new CreateAssetCommand(
                CHORD_CHART,
                "Lead Sheet",
                "Licensed chart.",
                "planner@cadentia.test",
                "Worship Team",
                WORSHIP_TEAM,
                AVAILABLE,
                "planner@cadentia.test"));
        versionOneId = assetRepository.createVersion(version(1, AVAILABLE, true)).id();
        versionTwoId = assetRepository.createVersion(version(2, AVAILABLE, true)).id();
    }

    private CreateAssetAttachmentCommand command(
            AssetModels.AssetAttachmentTargetTypeCode targetTypeCode,
            UUID targetId,
            UUID serviceContextId,
            AssetModels.AssetAttachmentPurposeCode purposeCode,
            int sortOrder) {
        return new CreateAssetAttachmentCommand(
                targetTypeCode,
                targetId,
                serviceContextId,
                versionOneId,
                CHORD_CHART,
                "Lead Sheet",
                sortOrder,
                purposeCode,
                true,
                Instant.parse("2026-07-01T00:00:00Z"),
                null,
                WORSHIP_TEAM,
                "planner@cadentia.test");
    }

    private CreateAssetVersionCommand version(int versionNumber, AssetModels.AssetLifecycleStatusCode status, boolean makeCurrent) {
        UUID assetId = jdbcTemplate.queryForObject("SELECT id FROM logical_assets LIMIT 1", Map.of(), UUID.class);
        return new CreateAssetVersionCommand(
                assetId,
                versionNumber,
                "rev-" + versionNumber,
                "S3_COMPATIBLE",
                "us-east-1",
                "worship-media",
                "assets/charts/lead-sheet-v" + versionNumber + ".pdf",
                "SHA-256",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "application/pdf",
                2048,
                "fixture://lead-sheet",
                "Imported from licensed source after catalog review.",
                "planner@cadentia.test",
                status,
                READY,
                WORSHIP_TEAM,
                new LicenseMetadata(
                        CCLI_COVERED,
                        "CCLI SongSelect",
                        "CCLI-1234567",
                        "Use only for worship-team rehearsals and services.",
                        "Cadentia Test Church",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null,
                        WORSHIP_TEAM),
                makeCurrent);
    }
}

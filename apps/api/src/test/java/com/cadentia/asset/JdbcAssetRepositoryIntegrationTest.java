package com.cadentia.asset;

import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.ADMINS_ONLY;
import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.WORSHIP_TEAM;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.CCLI_COVERED;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.EXPIRES;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.READY;
import static com.cadentia.asset.AssetModels.AssetTypeCode.CHORD_CHART;
import static com.cadentia.asset.AssetModels.AssetTypeCode.PDF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.asset.AssetModels.CreateAssetCommand;
import com.cadentia.asset.AssetModels.CreateAssetVersionCommand;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAssetRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcAssetRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        repository = new JdbcAssetRepository(jdbcTemplate);
    }

    @Test
    void seedsControlledVocabularyForCoreAssetsAndLocalExtensions() {
        // Arrange / Act / Assert
        assertThat(repository.listAssetTypes())
                .extracting(AssetModels.ControlledVocabularyRecord::code)
                .contains(
                        "PDF",
                        "CHORD_CHART",
                        "STEM",
                        "BACKING_TRACK",
                        "CLICK_TRACK",
                        "MIDI_CUE",
                        "REHEARSAL_RECORDING",
                        "PREVIEW",
                        "LOCAL_EXTENSION");
        assertThat(repository.listLifecycleStatuses())
                .extracting(AssetModels.ControlledVocabularyRecord::code)
                .contains("DRAFT", "AVAILABLE", "ARCHIVED", "QUARANTINED", "REPLACED");
        assertThat(repository.listProcessingStatuses())
                .extracting(AssetModels.ControlledVocabularyRecord::code)
                .contains("PENDING_SCAN", "READY", "FAILED", "REJECTED");
        assertThat(repository.listLicenseStatuses())
                .extracting(AssetModels.ControlledVocabularyRecord::code)
                .contains("UNKNOWN", "CCLI_COVERED", "DIRECT_PERMISSION", "EXPIRES", "REVOKED");
        assertThat(repository.listAccessPolicies())
                .extracting(AssetModels.ControlledVocabularyRecord::code)
                .contains("PUBLIC_METADATA", "WORSHIP_TEAM", "ADMINS_ONLY", "LOCAL_POLICY");
    }

    @Test
    void createsLogicalAssetAndImmutableVersionWithLicensingMetadata() {
        // Arrange
        var asset = repository.createAsset(new CreateAssetCommand(
                CHORD_CHART,
                "Lead Sheet PDF",
                "Metadata for a licensed lead sheet.",
                "planner@cadentia.test",
                "Worship Team",
                WORSHIP_TEAM,
                AVAILABLE,
                "planner@cadentia.test"));

        // Act
        var version = repository.createVersion(new CreateAssetVersionCommand(
                asset.id(),
                1,
                "rev-a",
                "S3_COMPATIBLE",
                "us-east-1",
                "worship-media",
                "assets/charts/lead-sheet-rev-a.pdf",
                "SHA-256",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "application/pdf",
                123_456,
                "https://example.invalid/source/lead-sheet",
                "Imported from licensed source after catalog review.",
                "planner@cadentia.test",
                AVAILABLE,
                READY,
                WORSHIP_TEAM,
                new LicenseMetadata(
                        CCLI_COVERED,
                        "CCLI SongSelect",
                        "CCLI-1234567",
                        "Use only for active worship-team rehearsals and services.",
                        "Cadentia Test Church",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null,
                        WORSHIP_TEAM),
                true));

        // Assert
        var reloaded = repository.findAsset(asset.id()).orElseThrow();
        assertThat(reloaded.stableIdentifier()).isNotNull();
        assertThat(reloaded.currentAssetVersionId()).isEqualTo(version.id());
        assertThat(reloaded.versions()).singleElement().satisfies(record -> {
            assertThat(record.stableIdentifier()).isNotNull();
            assertThat(record.assetId()).isEqualTo(asset.id());
            assertThat(record.versionNumber()).isEqualTo(1);
            assertThat(record.storageKey()).isEqualTo("assets/charts/lead-sheet-rev-a.pdf");
            assertThat(record.checksumAlgorithm()).isEqualTo("SHA-256");
            assertThat(record.mimeType()).isEqualTo("application/pdf");
            assertThat(record.byteSize()).isEqualTo(123_456);
            assertThat(record.lifecycleStatusCode()).isEqualTo(AVAILABLE);
            assertThat(record.processingStatusCode()).isEqualTo(READY);
            assertThat(record.licenseMetadata().licenseStatusCode()).isEqualTo(CCLI_COVERED);
            assertThat(record.licenseMetadata().usageRestrictions())
                    .contains("active worship-team rehearsals");
        });
    }

    @Test
    void rejectsDuplicateVersionNumbersMissingChecksumsInvalidSizesAndExpirationRanges() {
        // Arrange
        var asset = createPdfAsset();
        repository.createVersion(validVersion(asset.id(), 1));

        // Act / Assert
        assertThatThrownBy(() -> repository.createVersion(validVersion(asset.id(), 1)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.createVersion(versionWithChecksum(asset.id(), null, "bbbb")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.createVersion(versionWithChecksum(asset.id(), "SHA-256", "   ")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.createVersion(versionWithByteSize(asset.id(), 0)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.createVersion(versionWithExpirationRange(
                        asset.id(),
                        Instant.parse("2026-12-31T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"))))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsOrphanedVersionAndInvalidControlledVocabularyValues() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> repository.createVersion(validVersion(UUID.randomUUID(), 1)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO logical_assets (
                            asset_type_code, title, owner_actor, default_access_policy_code,
                            lifecycle_status_code, created_by
                        ) VALUES (
                            'UNVALIDATED_STRING', 'Invalid', 'actor', 'WORSHIP_TEAM', 'AVAILABLE', 'actor'
                        )
                        """,
                        Map.of()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void preventsInPlaceMutationOfReferenceableVersionRows() {
        // Arrange
        var asset = createPdfAsset();
        var version = repository.createVersion(validVersion(asset.id(), 1));

        // Act / Assert
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE asset_versions SET storage_key = :storageKey WHERE id = :versionId",
                        Map.of("storageKey", "mutated/key.pdf", "versionId", version.id())))
                .isInstanceOf(DataAccessException.class);
    }

    private AssetModels.AssetRecord createPdfAsset() {
        return repository.createAsset(new CreateAssetCommand(
                PDF,
                "Reference PDF",
                "Reference PDF metadata.",
                "assets-admin@cadentia.test",
                "Music Library",
                WORSHIP_TEAM,
                AVAILABLE,
                "assets-admin@cadentia.test"));
    }

    private CreateAssetVersionCommand validVersion(UUID assetId, int versionNumber) {
        return new CreateAssetVersionCommand(
                assetId,
                versionNumber,
                "rev-" + versionNumber,
                "S3_COMPATIBLE",
                "us-east-1",
                "worship-media",
                "assets/reference/version-" + versionNumber + ".pdf",
                "SHA-256",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "application/pdf",
                2048,
                "fixture://reference-pdf",
                "Verified fixture provenance.",
                "assets-admin@cadentia.test",
                AVAILABLE,
                READY,
                WORSHIP_TEAM,
                new LicenseMetadata(
                        CCLI_COVERED,
                        "Fixture License Source",
                        "FIXTURE-1",
                        "Fixture usage restriction.",
                        "Cadentia Test Church",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null,
                        WORSHIP_TEAM),
                false);
    }

    private CreateAssetVersionCommand versionWithChecksum(UUID assetId, String algorithm, String value) {
        CreateAssetVersionCommand valid = validVersion(assetId, 2);
        return new CreateAssetVersionCommand(
                assetId,
                valid.versionNumber(),
                valid.revisionCode(),
                valid.storageProviderCode(),
                valid.storageRegion(),
                valid.storageBucketAlias(),
                valid.storageKey(),
                algorithm,
                value,
                valid.mimeType(),
                valid.byteSize(),
                valid.sourceUri(),
                valid.provenanceSummary(),
                valid.createdBy(),
                valid.lifecycleStatusCode(),
                valid.processingStatusCode(),
                valid.accessPolicyCode(),
                valid.licenseMetadata(),
                valid.makeCurrent());
    }

    private CreateAssetVersionCommand versionWithByteSize(UUID assetId, long byteSize) {
        CreateAssetVersionCommand valid = validVersion(assetId, 3);
        return new CreateAssetVersionCommand(
                assetId,
                valid.versionNumber(),
                valid.revisionCode(),
                valid.storageProviderCode(),
                valid.storageRegion(),
                valid.storageBucketAlias(),
                valid.storageKey(),
                valid.checksumAlgorithm(),
                valid.checksumValue(),
                valid.mimeType(),
                byteSize,
                valid.sourceUri(),
                valid.provenanceSummary(),
                valid.createdBy(),
                valid.lifecycleStatusCode(),
                valid.processingStatusCode(),
                valid.accessPolicyCode(),
                valid.licenseMetadata(),
                valid.makeCurrent());
    }

    private CreateAssetVersionCommand versionWithExpirationRange(UUID assetId, Instant effectiveAt, Instant expiresAt) {
        CreateAssetVersionCommand valid = validVersion(assetId, 4);
        return new CreateAssetVersionCommand(
                assetId,
                valid.versionNumber(),
                valid.revisionCode(),
                valid.storageProviderCode(),
                valid.storageRegion(),
                valid.storageBucketAlias(),
                valid.storageKey(),
                valid.checksumAlgorithm(),
                valid.checksumValue(),
                valid.mimeType(),
                valid.byteSize(),
                valid.sourceUri(),
                valid.provenanceSummary(),
                valid.createdBy(),
                valid.lifecycleStatusCode(),
                valid.processingStatusCode(),
                ADMINS_ONLY,
                new LicenseMetadata(
                        EXPIRES,
                        "Expiring direct permission",
                        "EXP-1",
                        "Only until expiration.",
                        "Cadentia Test Church",
                        effectiveAt,
                        expiresAt,
                        ADMINS_ONLY),
                valid.makeCurrent());
    }
}

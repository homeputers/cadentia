package com.cadentia.asset;

import static com.cadentia.api.security.RbacAuthorities.ROLE_ADMIN;
import static com.cadentia.api.security.RbacAuthorities.ROLE_ASSIGNED_MUSICIAN;
import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.ADMINS_ONLY;
import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.SERVICE_PARTICIPANTS;
import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.WORSHIP_TEAM;
import static com.cadentia.asset.AssetModels.AssetAttachmentPurposeCode.PRIMARY_CHART;
import static com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode.ARRANGEMENT;
import static com.cadentia.asset.AssetModels.AssetAttachmentTargetTypeCode.SERVICE_ITEM;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.CCLI_COVERED;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.EXPIRED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.PENDING_SCAN;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.READY;
import static com.cadentia.asset.AssetModels.AssetTypeCode.BACKING_TRACK;
import static com.cadentia.asset.AssetModels.AssetTypeCode.CHORD_CHART;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cadentia.api.security.AssetAuthorizationPolicy;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetActor;
import com.cadentia.api.security.InMemoryAssetAuditRecorder;
import com.cadentia.asset.AssetModels.AssetAttachmentRecord;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import com.cadentia.asset.AssetWorkflowModels.AssetDiagnosticCode;
import com.cadentia.asset.AssetWorkflowModels.ServiceAssetContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetWorkflowResolutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-16T12:00:00Z");
    private final UUID arrangementId = UUID.randomUUID();
    private final UUID serviceItemId = UUID.randomUUID();
    private final UUID servicePlanId = UUID.randomUUID();
    private final UUID rehearsalSessionId = UUID.randomUUID();
    private AssetRepository assetRepository;
    private AssetAttachmentRepository attachmentRepository;
    private AssetWorkflowResolutionService service;

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        attachmentRepository = mock(AssetAttachmentRepository.class);
        service = new AssetWorkflowResolutionService(
                attachmentRepository,
                assetRepository,
                new AssetAuthorizationPolicy(new InMemoryAssetAuditRecorder(), Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void resolvesPreferredAccessibleServiceItemVersionBeforeArrangementFallbackAndPinsIt() {
        // Arrange
        AssetVersionRecord oldChart = version(CHORD_CHART, 1, READY, CCLI_COVERED, WORSHIP_TEAM, "application/pdf");
        AssetVersionRecord serviceOverrideChart = version(CHORD_CHART, 2, READY, CCLI_COVERED, SERVICE_PARTICIPANTS, "application/pdf");
        AssetAttachmentRecord oldAttachment = attachment(ARRANGEMENT, arrangementId, oldChart, CHORD_CHART, 20, false);
        AssetAttachmentRecord overrideAttachment = attachment(SERVICE_ITEM, serviceItemId, serviceOverrideChart, CHORD_CHART, 1, true);
        when(attachmentRepository.listAttachments(ARRANGEMENT, arrangementId)).thenReturn(List.of(oldAttachment));
        when(attachmentRepository.listAttachments(SERVICE_ITEM, serviceItemId)).thenReturn(List.of(overrideAttachment));
        when(assetRepository.findVersion(oldChart.id())).thenReturn(Optional.of(oldChart));
        when(assetRepository.findVersion(serviceOverrideChart.id())).thenReturn(Optional.of(serviceOverrideChart));

        // Act
        var resolved = service.resolveAssets(List.of(CHORD_CHART), context("application/"), musician());
        var pins = service.pinResolvedVersions(servicePlanId, UUID.randomUUID(), UUID.randomUUID(), arrangementId, resolved, "leader");

        // Assert
        assertThat(resolved).singleElement().satisfies(asset -> {
            assertThat(asset.accessible()).isTrue();
            assertThat(asset.version().id()).isEqualTo(serviceOverrideChart.id());
            assertThat(asset.diagnostics()).isEmpty();
        });
        assertThat(pins).singleElement().satisfies(pin -> {
            assertThat(pin.assetVersionId()).isEqualTo(serviceOverrideChart.id());
            assertThat(pin.attachmentId()).isEqualTo(overrideAttachment.id());
            assertThat(pin.pinnedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void emitsRedactedDiagnosticsForMissingInaccessibleExpiredPendingScanAndIncompatibleAssets() {
        // Arrange
        AssetVersionRecord adminOnly = version(BACKING_TRACK, 1, READY, CCLI_COVERED, ADMINS_ONLY, "audio/mpeg");
        AssetVersionRecord pending = version(CHORD_CHART, 1, PENDING_SCAN, CCLI_COVERED, WORSHIP_TEAM, "application/pdf");
        AssetVersionRecord expired = version(CHORD_CHART, 2, READY, EXPIRED, WORSHIP_TEAM, "application/pdf");
        AssetVersionRecord incompatible = version(CHORD_CHART, 3, READY, CCLI_COVERED, WORSHIP_TEAM, "text/plain");
        List<AssetAttachmentRecord> attachments = List.of(
                attachment(ARRANGEMENT, arrangementId, adminOnly, BACKING_TRACK, 1, true),
                attachment(ARRANGEMENT, arrangementId, pending, CHORD_CHART, 2, true),
                attachment(ARRANGEMENT, arrangementId, expired, CHORD_CHART, 3, true),
                attachment(ARRANGEMENT, arrangementId, incompatible, CHORD_CHART, 4, true));
        when(attachmentRepository.listAttachments(ARRANGEMENT, arrangementId)).thenReturn(attachments);
        when(attachmentRepository.listAttachments(SERVICE_ITEM, serviceItemId)).thenReturn(List.of());
        for (AssetVersionRecord version : List.of(adminOnly, pending, expired, incompatible)) {
            when(assetRepository.findVersion(version.id())).thenReturn(Optional.of(version));
        }

        // Act
        var diagnostics = service.diagnosticsForContext(
                List.of(BACKING_TRACK, CHORD_CHART, AssetModels.AssetTypeCode.MIDI_CUE),
                context("application/pdf"),
                musician());

        // Assert
        assertThat(diagnostics)
                .extracting("code")
                .contains(
                        AssetDiagnosticCode.INACCESSIBLE,
                        AssetDiagnosticCode.EXPIRED_LICENSE,
                        AssetDiagnosticCode.PENDING_SCAN,
                        AssetDiagnosticCode.INCOMPATIBLE_VERSION,
                        AssetDiagnosticCode.MISSING);
        assertThat(diagnostics).allSatisfy(diagnostic ->
                assertThat(diagnostic.message()).doesNotContain("CCLI", "license holder", "reference"));
    }

    @Test
    void roleSpecificRehearsalVisibilityDoesNotExposeAdminOnlyAssetToMusician() {
        // Arrange
        AssetVersionRecord adminOnly = version(CHORD_CHART, 1, READY, CCLI_COVERED, ADMINS_ONLY, "application/pdf");
        AssetAttachmentRecord attachment = attachment(ARRANGEMENT, arrangementId, adminOnly, CHORD_CHART, 1, true);
        when(attachmentRepository.listAttachments(ARRANGEMENT, arrangementId)).thenReturn(List.of(attachment));
        when(attachmentRepository.listAttachments(SERVICE_ITEM, serviceItemId)).thenReturn(List.of());
        when(assetRepository.findVersion(adminOnly.id())).thenReturn(Optional.of(adminOnly));

        // Act
        var musicianAsset = service.preferredAsset(CHORD_CHART, context("application/pdf"), musician()).orElseThrow();
        var adminAsset = service.preferredAsset(CHORD_CHART, context("application/pdf"), admin()).orElseThrow();

        // Assert
        assertThat(musicianAsset.accessible()).isFalse();
        assertThat(adminAsset.accessible()).isTrue();
    }

    private ServiceAssetContext context(String requiredMimeTypePrefix) {
        return new ServiceAssetContext(
                "church-a",
                servicePlanId,
                rehearsalSessionId,
                arrangementId,
                serviceItemId,
                requiredMimeTypePrefix,
                NOW);
    }

    private AssetActor musician() {
        return new AssetActor("musician", "church-a", Set.of(ROLE_ASSIGNED_MUSICIAN), true);
    }

    private AssetActor admin() {
        return new AssetActor("admin", "church-a", Set.of(ROLE_ADMIN), true);
    }

    private AssetAttachmentRecord attachment(
            AssetModels.AssetAttachmentTargetTypeCode targetType,
            UUID targetId,
            AssetVersionRecord version,
            AssetModels.AssetTypeCode type,
            int sortOrder,
            boolean required) {
        return new AssetAttachmentRecord(
                UUID.randomUUID(),
                targetType,
                targetId,
                servicePlanId,
                version.id(),
                type,
                type.name(),
                sortOrder,
                PRIMARY_CHART,
                required,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                WORSHIP_TEAM,
                null,
                null,
                null,
                "leader",
                "leader",
                NOW,
                NOW);
    }

    private AssetVersionRecord version(
            AssetModels.AssetTypeCode type,
            int versionNumber,
            AssetModels.AssetProcessingStatusCode processing,
            AssetModels.AssetLicenseStatusCode license,
            AssetModels.AssetAccessPolicyCode access,
            String mimeType) {
        return new AssetVersionRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                versionNumber,
                "rev-" + versionNumber,
                "LOCAL_FILESYSTEM",
                null,
                "local:local-development",
                "asset/" + type + "/v" + versionNumber,
                "SHA-256",
                "checksum",
                mimeType,
                128L,
                "https://example.invalid/source",
                "Fixture provenance",
                "leader",
                NOW,
                AVAILABLE,
                processing,
                access,
                new LicenseMetadata(
                        license,
                        "redacted-source",
                        "private-reference",
                        "private restrictions",
                        "private holder",
                        NOW.minusSeconds(60),
                        license == EXPIRED ? NOW.minusSeconds(1) : NOW.plusSeconds(3600),
                        ADMINS_ONLY));
    }
}

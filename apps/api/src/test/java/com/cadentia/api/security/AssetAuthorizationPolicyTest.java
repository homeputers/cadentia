package com.cadentia.api.security;

import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetAction.GENERATE_SIGNED_DOWNLOAD_URL;
import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetAction.READ_METADATA;
import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetAction.READ_PRIVATE_LICENSING_FIELDS;
import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetDenialReason.CROSS_INSTANCE;
import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetDenialReason.LICENSE_EXPIRED;
import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetDenialReason.LICENSE_VISIBILITY_DENIED;
import static com.cadentia.api.security.RbacAuthorities.ROLE_ADMIN;
import static com.cadentia.api.security.RbacAuthorities.ROLE_ASSIGNED_MUSICIAN;
import static com.cadentia.api.security.RbacAuthorities.ROLE_CATALOG_EDITOR;
import static com.cadentia.api.security.RbacAuthorities.ROLE_REPORTING_VIEWER;
import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAccessContext;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetActor;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuthorizationDecision;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuthorizationRequest;
import com.cadentia.asset.AssetModels;
import com.cadentia.asset.AssetModels.AssetAccessPolicyCode;
import com.cadentia.asset.AssetModels.AssetLicenseStatusCode;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetAuthorizationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-06-16T12:00:00Z");
    private static final String INSTANCE_ID = "church-a";

    private InMemoryAssetAuditRecorder auditRecorder;
    private AssetAuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        auditRecorder = new InMemoryAssetAuditRecorder();
        policy = new AssetAuthorizationPolicy(auditRecorder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void permitsReporterMetadataButRedactsPrivateLicensingFields() {
        // Arrange
        AssetAuthorizationRequest request = request(
                READ_METADATA,
                actor("reporter", ROLE_REPORTING_VIEWER),
                version(AssetAccessPolicyCode.PUBLIC_METADATA, AssetAccessPolicyCode.CATALOG_REVIEWERS, null),
                AssetAccessContext.none());

        // Act
        AssetAuthorizationDecision metadataDecision = policy.authorize(request);
        var license = policy.licenseFor(request);

        // Assert
        assertThat(metadataDecision.permitted()).isTrue();
        assertThat(license.licenseSource()).isEqualTo("CCLI SongSelect");
        assertThat(license.licenseStatus()).isEqualTo("CCLI_COVERED");
        assertThat(license.usageRestrictions()).isEqualTo("Rehearsal and service use only");
        assertThat(license.privateFieldsVisible()).isFalse();
        assertThat(license.licenseReference()).isNull();
        assertThat(auditRecorder.records()).singleElement().satisfies(record -> {
            assertThat(record.actionCode()).isEqualTo(READ_PRIVATE_LICENSING_FIELDS);
            assertThat(record.permitted()).isFalse();
            assertThat(record.reasonCode()).isEqualTo(LICENSE_VISIBILITY_DENIED);
        });
    }

    @Test
    void permitsCatalogReviewerPrivateLicensingFields() {
        // Arrange
        AssetAuthorizationRequest request = request(
                READ_PRIVATE_LICENSING_FIELDS,
                actor("reviewer", ROLE_CATALOG_EDITOR),
                version(AssetAccessPolicyCode.CATALOG_REVIEWERS, AssetAccessPolicyCode.CATALOG_REVIEWERS, null),
                AssetAccessContext.none());

        // Act
        var license = policy.licenseFor(request);

        // Assert
        assertThat(license.privateFieldsVisible()).isTrue();
        assertThat(license.licenseReference()).isEqualTo("CCLI-12345");
        assertThat(license.licenseHolder()).isEqualTo("Cadentia Test Church");
    }

    @Test
    void permitsAssignedMusicianSignedAccessWhenScopedToServiceAssignment() {
        // Arrange
        UUID servicePlanId = UUID.randomUUID();
        AssetAuthorizationRequest request = request(
                GENERATE_SIGNED_DOWNLOAD_URL,
                actor("musician", ROLE_ASSIGNED_MUSICIAN),
                version(AssetAccessPolicyCode.SERVICE_PARTICIPANTS, AssetAccessPolicyCode.SERVICE_PARTICIPANTS, null),
                new AssetAccessContext(servicePlanId, null, UUID.randomUUID(), true, false, true));

        // Act
        AssetAuthorizationDecision decision = policy.authorize(request);

        // Assert
        assertThat(decision.permitted()).isTrue();
        assertThat(auditRecorder.records()).singleElement().satisfies(record -> {
            assertThat(record.actionCode()).isEqualTo(GENERATE_SIGNED_DOWNLOAD_URL);
            assertThat(record.servicePlanId()).isEqualTo(servicePlanId);
            assertThat(record.permitted()).isTrue();
        });
    }

    @Test
    void deniesCrossInstanceAccessAndAuditsSensitiveAttempt() {
        // Arrange
        AssetAuthorizationRequest request = new AssetAuthorizationRequest(
                GENERATE_SIGNED_DOWNLOAD_URL,
                actor("admin", ROLE_ADMIN),
                "church-b",
                null,
                version(AssetAccessPolicyCode.WORSHIP_TEAM, AssetAccessPolicyCode.WORSHIP_TEAM, null),
                null,
                AssetAccessContext.none());

        // Act
        AssetAuthorizationDecision decision = policy.authorize(request);

        // Assert
        assertThat(decision.permitted()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo(CROSS_INSTANCE);
        assertThat(auditRecorder.records()).singleElement().satisfies(record -> {
            assertThat(record.actorId()).isEqualTo("admin");
            assertThat(record.permitted()).isFalse();
            assertThat(record.reasonCode()).isEqualTo(CROSS_INSTANCE);
        });
    }

    @Test
    void blocksExpiredLicenseForNormalDownload() {
        // Arrange
        AssetAuthorizationRequest request = request(
                GENERATE_SIGNED_DOWNLOAD_URL,
                actor("musician", ROLE_ASSIGNED_MUSICIAN),
                version(AssetAccessPolicyCode.WORSHIP_TEAM, AssetAccessPolicyCode.WORSHIP_TEAM, NOW.minusSeconds(1)),
                AssetAccessContext.none());

        // Act
        AssetAuthorizationDecision decision = policy.authorize(request);

        // Assert
        assertThat(decision.permitted()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo(LICENSE_EXPIRED);
        assertThat(auditRecorder.records()).singleElement().satisfies(record -> {
            assertThat(record.reasonCode()).isEqualTo(LICENSE_EXPIRED);
            assertThat(record.referenceMetadata()).containsEntry("licenseStatus", "CCLI_COVERED");
        });
    }

    private AssetAuthorizationRequest request(
            AssetAuthorizationPolicy.AssetAction action,
            AssetActor actor,
            AssetVersionRecord version,
            AssetAccessContext context) {
        return new AssetAuthorizationRequest(action, actor, INSTANCE_ID, null, version, null, context);
    }

    private AssetActor actor(String actorId, String role) {
        return new AssetActor(actorId, INSTANCE_ID, Set.of(role), true);
    }

    private AssetVersionRecord version(
            AssetAccessPolicyCode accessPolicy,
            AssetAccessPolicyCode licenseVisibilityPolicy,
            Instant expiresAt) {
        return new AssetVersionRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "rev-a",
                "LOCAL_FILESYSTEM",
                null,
                "local:local-development",
                "local-development/assets/asset/v1/object",
                "SHA-256",
                "checksum",
                "application/pdf",
                128L,
                "https://example.invalid/source",
                "Fixture provenance",
                "planner",
                NOW,
                AssetModels.AssetLifecycleStatusCode.AVAILABLE,
                AssetModels.AssetProcessingStatusCode.READY,
                accessPolicy,
                new LicenseMetadata(
                        AssetLicenseStatusCode.CCLI_COVERED,
                        "CCLI SongSelect",
                        "CCLI-12345",
                        "Rehearsal and service use only",
                        "Cadentia Test Church",
                        NOW.minusSeconds(60),
                        expiresAt,
                        licenseVisibilityPolicy));
    }
}

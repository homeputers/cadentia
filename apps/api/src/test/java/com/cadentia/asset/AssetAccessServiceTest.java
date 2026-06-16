package com.cadentia.asset;

import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetAction.GENERATE_SIGNED_DOWNLOAD_URL;
import static com.cadentia.api.security.AssetAuthorizationPolicy.AssetDenialReason.QUARANTINED_VERSION;
import static com.cadentia.api.security.RbacAuthorities.ROLE_ADMIN;
import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.WORSHIP_TEAM;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.QUARANTINED;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.CCLI_COVERED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.api.security.AssetAuthorizationPolicy;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAccessContext;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetActor;
import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuthorizationRequest;
import com.cadentia.api.security.InMemoryAssetAuditRecorder;
import com.cadentia.asset.AssetModels.AssetLifecycleStatusCode;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import com.cadentia.asset.AssetStorageAdapter.SignedStorageUrl;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetAccessServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-16T12:00:00Z");
    private static final String STORAGE_KEY = "local-development/assets/asset/v1/object";

    private AssetStorageAdapter storageAdapter;
    private InMemoryAssetAuditRecorder auditRecorder;
    private AssetAccessService service;

    @BeforeEach
    void setUp() {
        storageAdapter = mock(AssetStorageAdapter.class);
        auditRecorder = new InMemoryAssetAuditRecorder();
        var properties = new AssetStorageProperties();
        properties.setSignedDownloadUrlTtl(Duration.ofMinutes(5));
        service = new AssetAccessService(
                new AssetAuthorizationPolicy(auditRecorder, Clock.fixed(NOW, ZoneOffset.UTC)),
                storageAdapter,
                properties);
        when(storageAdapter.signedDownloadUrl(eq(STORAGE_KEY), any()))
                .thenReturn(new SignedStorageUrl(URI.create("local-asset://signed-download"), NOW.plusSeconds(300), "GET", STORAGE_KEY));
    }

    @Test
    void generatesSignedDownloadOnlyAfterAuthorizationPasses() {
        // Arrange
        AssetAuthorizationRequest request = request(AVAILABLE);

        // Act
        var access = service.authorizeSignedDownload(request);

        // Assert
        assertThat(access.decision().permitted()).isTrue();
        assertThat(access.signedUrl()).isNotNull();
        assertThat(access.signedUrl().url()).hasToString("local-asset://signed-download");
        verify(storageAdapter).signedDownloadUrl(eq(STORAGE_KEY), eq(Duration.ofMinutes(5)));
        assertThat(auditRecorder.records()).singleElement().satisfies(record -> {
            assertThat(record.actionCode()).isEqualTo(GENERATE_SIGNED_DOWNLOAD_URL);
            assertThat(record.permitted()).isTrue();
        });
    }

    @Test
    void doesNotGenerateSignedDownloadWhenLifecycleIsBlocked() {
        // Arrange
        AssetAuthorizationRequest request = request(QUARANTINED);

        // Act
        var access = service.authorizeSignedDownload(request);

        // Assert
        assertThat(access.decision().permitted()).isFalse();
        assertThat(access.decision().reasonCode()).isEqualTo(QUARANTINED_VERSION);
        assertThat(access.signedUrl()).isNull();
        verify(storageAdapter, never()).signedDownloadUrl(any(), any());
        assertThat(auditRecorder.records()).singleElement().satisfies(record -> {
            assertThat(record.permitted()).isFalse();
            assertThat(record.reasonCode()).isEqualTo(QUARANTINED_VERSION);
        });
    }

    private AssetAuthorizationRequest request(AssetLifecycleStatusCode lifecycleStatusCode) {
        return new AssetAuthorizationRequest(
                GENERATE_SIGNED_DOWNLOAD_URL,
                new AssetActor("admin", "church-a", Set.of(ROLE_ADMIN), true),
                "church-a",
                null,
                version(lifecycleStatusCode),
                null,
                AssetAccessContext.none());
    }

    private AssetVersionRecord version(AssetLifecycleStatusCode lifecycleStatusCode) {
        return new AssetVersionRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "rev-a",
                "LOCAL_FILESYSTEM",
                null,
                "local:local-development",
                STORAGE_KEY,
                "SHA-256",
                "checksum",
                "application/pdf",
                128L,
                "https://example.invalid/source",
                "Fixture provenance",
                "planner",
                NOW,
                lifecycleStatusCode,
                READY,
                WORSHIP_TEAM,
                new LicenseMetadata(
                        CCLI_COVERED,
                        "CCLI SongSelect",
                        "CCLI-12345",
                        "Rehearsal and service use only",
                        "Cadentia Test Church",
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(3600),
                        WORSHIP_TEAM));
    }
}

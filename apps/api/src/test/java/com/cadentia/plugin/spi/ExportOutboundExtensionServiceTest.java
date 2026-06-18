package com.cadentia.plugin.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.plugin.PluginModels.Environment;
import com.cadentia.plugin.PluginRegistryAuditRecorder;
import com.cadentia.plugin.policy.PluginPolicyModels.CanonicalPolicySnapshot;
import com.cadentia.plugin.policy.PluginPolicyModels.SanitizedPluginOutput;
import com.cadentia.plugin.runtime.PluginRuntimeGateway;
import com.cadentia.plugin.runtime.PluginRuntimeModels.ExecutionStatus;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginExecutionMetadata;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeInvocation;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeResult;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.ExportRequest;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.IntegrationFailureKind;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.JobStatus;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.OutboundPublishRequest;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.RetryPolicy;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.SourceType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportOutboundExtensionServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T00:00:00Z");
    private static final UUID PLUGIN_VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID CONFIGURATION_VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");

    private FakeRuntimeGateway runtimeService;
    private PluginRegistryAuditRecorder auditRecorder;
    private ExportOutboundExtensionService service;

    @BeforeEach
    void setUp() {
        runtimeService = new FakeRuntimeGateway();
        auditRecorder = new PluginRegistryAuditRecorder();
        service = new ExportOutboundExtensionService(runtimeService, auditRecorder);
    }

    @Test
    void createsAuditableExportArtifactWithSourcePluginConfigurationAndSafeProvenanceMetadata() {
        // Arrange
        runtimeService.result = success("EXPORT_RENDERER", output(List.of("song-1"), List.of("asset-1"), List.of(),
                List.of("CCLI"), Map.of(
                        "artifactRef", "artifact://exports/packet-1",
                        "mimeType", "application/pdf",
                        "filename", "packet.pdf",
                        "checksumSha256", "abc123",
                        "provenance.source", "setlist-version-1")));

        // Act
        var result = service.renderExport(invocation("EXPORT_RENDERER"), exportRequest(List.of("asset-1"), List.of()));

        // Assert
        assertThat(result.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(result.artifact().sourceSnapshotId()).isEqualTo("setlist-version-1");
        assertThat(result.artifact().pluginVersionId()).isEqualTo(PLUGIN_VERSION_ID);
        assertThat(result.artifact().configurationVersionId()).isEqualTo(CONFIGURATION_VERSION_ID);
        assertThat(result.artifact().generatedAt()).isEqualTo(NOW);
        assertThat(result.artifact().visibleSongIds()).containsExactly("song-1");
        assertThat(result.artifact().provenanceMetadata()).containsEntry("source", "setlist-version-1");
        assertThat(auditRecorder.events()).extracting(PluginRegistryAuditRecorder.PluginRegistryAuditEvent::action)
                .contains("PLUGIN_EXPORT_ARTIFACT_CREATED");
    }

    @Test
    void rejectsExportsContainingUnauthorizedAssetsOrReviewNotesAfterRuntimeFiltering() {
        // Arrange
        runtimeService.result = success("EXPORT_RENDERER", output(List.of("song-1"), List.of("asset-secret"),
                List.of("review-private"), List.of("CCLI"), Map.of(
                        "artifactRef", "artifact://exports/packet-1",
                        "mimeType", "application/pdf",
                        "filename", "packet.pdf",
                        "checksumSha256", "abc123")));

        // Act
        var result = service.renderExport(invocation("EXPORT_RENDERER"), exportRequest(List.of("asset-1"), List.of()));

        // Assert
        assertThat(result.status()).isEqualTo(JobStatus.DEGRADED);
        assertThat(result.artifact()).isNull();
        assertThat(result.safeErrors()).contains("PLUGIN_EXPORT_UNAUTHORIZED_CONTENT");
    }

    @Test
    void omitsLicenseRestrictedContentWhenExportOutputExceedsRequestedLicenseScope() {
        // Arrange
        runtimeService.result = success("EXPORT_RENDERER", output(List.of("song-1"), List.of("asset-1"), List.of(),
                List.of("CCLI", "STREAMING"), Map.of(
                        "artifactRef", "artifact://exports/packet-1",
                        "mimeType", "application/pdf",
                        "filename", "packet.pdf",
                        "checksumSha256", "abc123")));

        // Act
        var result = service.renderExport(invocation("EXPORT_RENDERER"), exportRequest(List.of("asset-1"), List.of()));

        // Assert
        assertThat(result.status()).isEqualTo(JobStatus.DEGRADED);
        assertThat(result.safeErrors()).contains("PLUGIN_EXPORT_LICENSE_SCOPE_DENIED");
    }

    @Test
    void duplicateOutboundRequestsAreSuppressedByIdempotencyKey() {
        // Arrange
        runtimeService.result = success("OUTBOUND_PUBLISH_HOOK", output(List.of(), List.of(), List.of(), List.of("CCLI"),
                Map.of("externalReference", "external-1", "reconciliationStatus", "DELIVERED")));

        // Act
        var first = service.publishOutbound(invocation("OUTBOUND_PUBLISH_HOOK"), outboundRequest("idem-1"));
        var duplicate = service.publishOutbound(invocation("OUTBOUND_PUBLISH_HOOK"), outboundRequest("idem-1"));

        // Assert
        assertThat(first.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(duplicate.status()).isEqualTo(JobStatus.DUPLICATE_SUPPRESSED);
        assertThat(duplicate.attempt().safeErrors()).contains("PLUGIN_OUTBOUND_DUPLICATE_SUPPRESSED");
    }

    @Test
    void disabledOutboundPluginReturnsSafeStatusWithoutExternalResult() {
        // Arrange
        runtimeService.result = new PluginRuntimeResult(ExecutionStatus.DEGRADED, List.of(), List.of(), List.of("PLUGIN_DISABLED"), true);

        // Act
        var result = service.publishOutbound(invocation("OUTBOUND_PUBLISH_HOOK"), outboundRequest("idem-disabled"));

        // Assert
        assertThat(result.status()).isEqualTo(JobStatus.DEGRADED);
        assertThat(result.attempt().idempotencyKey()).isEqualTo("idem-disabled");
        assertThat(result.attempt().safeErrors()).contains("PLUGIN_DISABLED");
    }

    @Test
    void recordsRetryableAndNonRetryableOutboundFailuresWithCorrelationAndIdempotency() {
        // Arrange
        runtimeService.result = new PluginRuntimeResult(ExecutionStatus.RETRY_SCHEDULED, List.of(), List.of(),
                List.of("PLUGIN_TIMEOUT"), true);

        // Act
        var retryable = service.publishOutbound(invocation("OUTBOUND_PUBLISH_HOOK"), outboundRequest("idem-2"));
        runtimeService.result = new PluginRuntimeResult(ExecutionStatus.FAILED_CLOSED, List.of(), List.of(),
                List.of("PLUGIN_POLICY_DENIED"), true);
        var nonRetryable = service.publishOutbound(invocation("OUTBOUND_PUBLISH_HOOK"), outboundRequest("idem-3"));

        // Assert
        assertThat(retryable.status()).isEqualTo(JobStatus.RETRY_SCHEDULED);
        assertThat(retryable.attempt().failureKind()).isEqualTo(IntegrationFailureKind.RETRYABLE);
        assertThat(retryable.attempt().correlationId()).isEqualTo("corr-1");
        assertThat(nonRetryable.status()).isEqualTo(JobStatus.FAILED);
        assertThat(nonRetryable.attempt().failureKind()).isEqualTo(IntegrationFailureKind.NON_RETRYABLE);
    }

    @Test
    void rejectsRawCredentialsBeforeCallingOutboundPlugin() {
        // Arrange
        OutboundPublishRequest request = new OutboundPublishRequest("service-plan-version-1", SourceType.SERVICE_PLAN,
                "service-plan.published.v1", "idem-raw", "corr-1", "secret:plain-token",
                new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(30)), Map.of("servicePlanId", "plan-1"));

        // Act
        var result = service.publishOutbound(invocation("OUTBOUND_PUBLISH_HOOK"), request);

        // Assert
        assertThat(runtimeService.called).isFalse();
        assertThat(result.status()).isEqualTo(JobStatus.FAILED);
        assertThat(result.attempt().safeErrors()).contains("PLUGIN_OUTBOUND_CREDENTIAL_REF_INVALID");
    }

    private ExportRequest exportRequest(List<String> authorizedAssets, List<String> authorizedReviewNotes) {
        return new ExportRequest("setlist-version-1", SourceType.SETLIST, "PDF", "REHEARSAL_PACKET", NOW, Map.of(),
                List.of("song-1"), authorizedAssets, authorizedReviewNotes, List.of("CCLI"));
    }

    private OutboundPublishRequest outboundRequest(String idempotencyKey) {
        return new OutboundPublishRequest("service-plan-version-1", SourceType.SERVICE_PLAN, "service-plan.published.v1",
                idempotencyKey, "corr-1", "credential-ref:planning-center:river",
                new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(30)), Map.of("servicePlanId", "plan-1"));
    }

    private PluginRuntimeResult success(String extensionPoint, SanitizedPluginOutput output) {
        var metadata = new PluginExecutionMetadata(PLUGIN_VERSION_ID, CONFIGURATION_VERSION_ID, "exporter", "1.2.3",
                extensionPoint, ExecutionStatus.SUCCEEDED, null, 1, "in", "out", 1);
        return new PluginRuntimeResult(ExecutionStatus.SUCCEEDED, List.of(output), List.of(metadata), List.of(), false);
    }

    private SanitizedPluginOutput output(List<String> songs, List<String> assets, List<String> notes, List<String> licenses,
            Map<String, Object> attributes) {
        return new SanitizedPluginOutput(songs, songs, List.of(), assets, List.of(), List.of("plan-1"), notes, licenses,
                List.of("river"), attributes, List.of());
    }

    private PluginRuntimeInvocation invocation(String extensionPoint) {
        return new PluginRuntimeInvocation("river", Environment.PRODUCTION, extensionPoint, "1.0.0", "actor",
                Set.of("WORSHIP_PLANNER"), Set.of("CCLI"), Map.of(), new CanonicalPolicySnapshot(Set.of("song-1"), Set.of("song-1"),
                Set.of(), Set.of("asset-1"), Set.of(), Set.of("plan-1"), Set.of(), Set.of("CCLI"), Set.of("WORSHIP_PLANNER"),
                Set.of(), Set.of("river"), Map.of("exporter", Set.of("CCLI"))), "catalog", "policy", 1L, false, "idem");
    }

    private static class FakeRuntimeGateway implements PluginRuntimeGateway {
        private PluginRuntimeResult result;
        private boolean called;

        @Override
        public PluginRuntimeResult execute(PluginRuntimeInvocation invocation) {
            called = true;
            return result;
        }
    }
}

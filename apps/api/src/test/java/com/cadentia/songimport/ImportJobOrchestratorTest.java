package com.cadentia.songimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportJobOrchestratorTest {

    private static final Instant NOW = Instant.parse("2026-05-19T12:00:00Z");

    @Test
    void succeedsWhenLifecycleCompletes() {
        // Given
        ImportJobOrchestrator orchestrator = orchestrator(2);

        // When
        ImportOrchestrationResult result = orchestrator.run(
                new SuccessfulAdapter(), context(), configuration(), false);

        // Then
        assertThat(result.job().status()).isEqualTo(ImportJobStatus.SUCCEEDED);
        assertThat(result.job().stagedCandidates()).hasSize(1);
    }

    @Test
    void returnsPartiallySucceededWhenSomeCandidatesAreStagedAndFailuresOccur() {
        // Given
        ImportJobOrchestrator orchestrator = orchestrator(1);

        // When
        ImportOrchestrationResult result = orchestrator.run(
                new PartiallyFailingAdapter(), context(), configuration(), false);

        // Then
        assertThat(result.job().status()).isEqualTo(ImportJobStatus.PARTIALLY_SUCCEEDED);
        assertThat(result.job().stagedCandidates()).hasSize(1);
        assertThat(result.job().sourceFailures()).hasSize(1);
    }

    @Test
    void failsWhenNonRetryableFailureOccurs() {
        // Given
        ImportJobOrchestrator orchestrator = orchestrator(3);

        // When
        ImportOrchestrationResult result = orchestrator.run(
                new NonRetryableFailingAdapter(), context(), configuration(), false);

        // Then
        assertThat(result.job().status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(result.job().attempts()).isEqualTo(1);
    }

    @Test
    void cancelsBeforeExecutionWhenCancellationRequested() {
        // Given
        ImportJobOrchestrator orchestrator = orchestrator(2);

        // When
        ImportOrchestrationResult result = orchestrator.run(
                new SuccessfulAdapter(), context(), configuration(), true);

        // Then
        assertThat(result.job().status()).isEqualTo(ImportJobStatus.CANCELED);
        assertThat(result.job().attempts()).isZero();
    }

    @Test
    void exhaustsRetriesForRetryableFailure() {
        // Given
        ImportJobOrchestrator orchestrator = orchestrator(2,
                (stage, exception) -> new ConnectorFailure(stage, ConnectorErrorCode.SOURCE_UNAVAILABLE, exception.getMessage(), true));

        // When
        ImportOrchestrationResult result = orchestrator.run(
                new RetryableFailingAdapter(), context(), configuration(), false);

        // Then
        assertThat(result.job().status()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(result.job().attempts()).isEqualTo(2);
        assertThat(result.job().auditEvents())
                .extracting(ImportJobAuditEvent::status)
                .contains(ImportJobStatus.RETRY_SCHEDULED);
    }

    private static ImportJobOrchestrator orchestrator(int maxAttempts) {
        return orchestrator(maxAttempts, new DefaultConnectorErrorTranslator());
    }

    private static ImportJobOrchestrator orchestrator(int maxAttempts, ConnectorErrorTranslator translator) {
        return new ImportJobOrchestrator(
                new ConnectorLifecycleRunner(new NoopImportPipeline(), translator),
                Clock.fixed(NOW, ZoneOffset.UTC),
                maxAttempts);
    }

    private static ConnectorExecutionContext context() {
        return new ConnectorExecutionContext(UUID.randomUUID(), UUID.randomUUID(), "op@test", NOW);
    }

    private static ConnectorConfiguration configuration() {
        return new ConnectorConfiguration("fixture", "rights", Map.of(), Map.of());
    }

    private static class SuccessfulAdapter extends BaseAdapter {
        @Override
        List<DiscoveredSource> discoveredSources() {
            return List.of(source("1"));
        }
    }

    private static class PartiallyFailingAdapter extends BaseAdapter {
        @Override
        List<DiscoveredSource> discoveredSources() {
            return List.of(source("1"), source("bad"));
        }

        @Override
        public SourcePayload fetch(ConnectorExecutionContext context, DiscoveredSource source) {
            if ("bad".equals(source.sourceRecordId())) {
                throw new IllegalArgumentException("boom");
            }
            return super.fetch(context, source);
        }
    }

    private static class NonRetryableFailingAdapter extends BaseAdapter {
        @Override
        List<DiscoveredSource> discoveredSources() {
            return List.of();
        }

        @Override
        public List<DiscoveredSource> discover(ConnectorExecutionContext context, ConnectorConfiguration configuration) {
            throw new IllegalArgumentException("bad input");
        }
    }

    private static class RetryableFailingAdapter extends BaseAdapter {
        @Override
        List<DiscoveredSource> discoveredSources() {
            return List.of();
        }

        @Override
        public List<DiscoveredSource> discover(ConnectorExecutionContext context, ConnectorConfiguration configuration) {
            throw new RuntimeException("temporary");
        }
    }

    private abstract static class BaseAdapter implements ProviderAdapter {

        @Override
        public ConnectorDescriptor descriptor() {
            return new ConnectorDescriptor(
                    "test", "Test", "desc", ImportMethod.CSV_IMPORT, LegalMode.ENABLED, CredentialRequirement.NONE,
                    Set.of(PayloadType.CSV), RateLimitPolicy.notApplicable(), AutomationLevel.OPERATOR_PROVIDED_FILE,
                    Set.of(ConnectorCapability.DISCOVER, ConnectorCapability.FETCH, ConnectorCapability.PARSE,
                            ConnectorCapability.NORMALIZE));
        }

        @Override
        public ConnectorConfiguration configure(ConnectorConfiguration configuration) {
            return configuration;
        }

        @Override
        public List<DiscoveredSource> discover(ConnectorExecutionContext context, ConnectorConfiguration configuration) {
            return discoveredSources();
        }

        abstract List<DiscoveredSource> discoveredSources();

        @Override
        public SourcePayload fetch(ConnectorExecutionContext context, DiscoveredSource source) {
            return new SourcePayload(source, "raw", "hash", NOW);
        }

        @Override
        public ConnectorNativeRecord parse(ConnectorExecutionContext context, SourcePayload payload) {
            return new ConnectorNativeRecord(payload, Map.of("title", "Song"), Map.of());
        }

        @Override
        public NormalizedImportCandidate normalize(ConnectorExecutionContext context, ConnectorNativeRecord nativeRecord) {
            return new NormalizedImportCandidate(
                    "test", "provider", ImportMethod.CSV_IMPORT, nativeRecord.payload().source().sourceRecordId(),
                    nativeRecord.payload().source().sourceReference(), LicenseType.CCLI, NOW, "raw", "norm",
                    "Song", "song", "Artist", null, "{}", Map.of());
        }

        static DiscoveredSource source(String id) {
            return new DiscoveredSource(id, PayloadType.CSV, "file:" + id, Map.of());
        }
    }

    private static class NoopImportPipeline implements SharedImportPipeline {
        @Override
        public CandidateValidationResult validate(ConnectorExecutionContext context, NormalizedImportCandidate candidate) {
            return CandidateValidationResult.valid(candidate);
        }

        @Override
        public StagedImportCandidate stage(ConnectorExecutionContext context, NormalizedImportCandidate candidate) {
            return new StagedImportCandidate(
                    context.importBatchId(), context.runId(), candidate.sourceRecordId(), candidate.normalizedTitle(),
                    ImportCandidateStatus.STAGED);
        }
    }
}

package com.cadentia.songimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorLifecycleRunnerTest {

    @Test
    void fakeConnectorTraversesLifecycleWithoutWritingApprovedCatalogRecords() {
        // Arrange
        UUID runId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        ConnectorExecutionContext context = new ConnectorExecutionContext(
                runId, importBatchId, "operator@example.test", Instant.parse("2026-05-19T12:00:00Z"));
        FakeProviderAdapter providerAdapter = new FakeProviderAdapter();
        RecordingImportPipeline importPipeline = new RecordingImportPipeline();
        ConnectorLifecycleRunner runner = new ConnectorLifecycleRunner(
                importPipeline, new DefaultConnectorErrorTranslator());

        // Act
        ConnectorLifecycleResult result = runner.run(
                providerAdapter,
                context,
                new ConnectorConfiguration(
                        "fake-manual-source",
                        "operator owns the entered payload",
                        Map.of("formId", "manual-1"),
                        Map.of()));

        // Assert
        assertThat(result.failures()).isEmpty();
        assertThat(result.stagedCandidates())
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.importBatchId()).isEqualTo(importBatchId);
                    assertThat(candidate.connectorRunId()).isEqualTo(runId);
                    assertThat(candidate.normalizedTitle()).isEqualTo("amazing grace");
                    assertThat(candidate.status()).isEqualTo(ImportCandidateStatus.STAGED);
                });
        assertThat(result.events())
                .extracting(ConnectorLifecycleEvent::stage)
                .containsExactly(
                        ConnectorLifecycleStage.CONFIGURE,
                        ConnectorLifecycleStage.DISCOVER,
                        ConnectorLifecycleStage.FETCH,
                        ConnectorLifecycleStage.PARSE,
                        ConnectorLifecycleStage.NORMALIZE,
                        ConnectorLifecycleStage.VALIDATE,
                        ConnectorLifecycleStage.STAGE);
        assertThat(importPipeline.validatedCandidates).hasSize(1);
        assertThat(importPipeline.stagedCandidates).hasSize(1);
        assertThat(importPipeline.approvedCatalogWriteAttempted).isFalse();
    }

    @Test
    void descriptorDeclaresConnectorPolicyAndAcquisitionCapabilities() {
        // Arrange
        FakeProviderAdapter providerAdapter = new FakeProviderAdapter();

        // Act
        ConnectorDescriptor descriptor = providerAdapter.descriptor();

        // Assert
        assertThat(descriptor.importMethod()).isEqualTo(ImportMethod.MANUAL_ENTRY);
        assertThat(descriptor.legalMode()).isEqualTo(LegalMode.ENABLED);
        assertThat(descriptor.credentialRequirement()).isEqualTo(CredentialRequirement.NONE);
        assertThat(descriptor.supportedPayloadTypes()).containsExactly(PayloadType.MANUAL_FORM);
        assertThat(descriptor.rateLimitPolicy().behavior()).isEqualTo(RateLimitBehavior.NOT_APPLICABLE);
        assertThat(descriptor.automationLevel()).isEqualTo(AutomationLevel.MANUAL);
        assertThat(descriptor.capabilities())
                .contains(
                        ConnectorCapability.DISCOVER,
                        ConnectorCapability.FETCH,
                        ConnectorCapability.PARSE,
                        ConnectorCapability.NORMALIZE);
    }

    @Test
    void policyBlockedConnectorReturnsFailureWithoutDiscoverOrFetch() {
        // Arrange
        UUID runId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        ConnectorExecutionContext context = new ConnectorExecutionContext(
                runId, importBatchId, "operator@example.test", Instant.parse("2026-05-19T12:00:00Z"));
        PolicyBlockedProviderAdapter providerAdapter = new PolicyBlockedProviderAdapter();
        RecordingImportPipeline importPipeline = new RecordingImportPipeline();
        ConnectorLifecycleRunner runner = new ConnectorLifecycleRunner(
                importPipeline, new DefaultConnectorErrorTranslator());

        // Act
        ConnectorLifecycleResult result = runner.run(
                providerAdapter,
                context,
                new ConnectorConfiguration("ultimate-guitar", "blocked", Map.of(), Map.of()));

        // Assert
        assertThat(result.events())
                .singleElement()
                .extracting(ConnectorLifecycleEvent::stage)
                .isEqualTo(ConnectorLifecycleStage.CONFIGURE);
        assertThat(result.stagedCandidates()).isEmpty();
        assertThat(result.failures())
                .singleElement()
                .satisfies(failure -> {
                    assertThat(failure.stage()).isEqualTo(ConnectorLifecycleStage.CONFIGURE);
                    assertThat(failure.errorCode()).isEqualTo(ConnectorErrorCode.POLICY_BLOCKED);
                    assertThat(failure.retryable()).isFalse();
                });
        assertThat(providerAdapter.discoverCalls).isZero();
        assertThat(providerAdapter.fetchCalls).isZero();
        assertThat(importPipeline.validatedCandidates).isEmpty();
        assertThat(importPipeline.stagedCandidates).isEmpty();
    }

    private static final class FakeProviderAdapter implements ProviderAdapter {

        @Override
        public ConnectorDescriptor descriptor() {
            return new ConnectorDescriptor(
                    "fake-manual",
                    "Fake Manual",
                    "Fake manual connector",
                    ImportMethod.MANUAL_ENTRY,
                    LegalMode.ENABLED,
                    CredentialRequirement.NONE,
                    Set.of(PayloadType.MANUAL_FORM),
                    RateLimitPolicy.notApplicable(),
                    AutomationLevel.MANUAL,
                    Set.of(
                            ConnectorCapability.DISCOVER,
                            ConnectorCapability.FETCH,
                            ConnectorCapability.PARSE,
                            ConnectorCapability.NORMALIZE));
        }

        @Override
        public ConnectorConfiguration configure(ConnectorConfiguration configuration) {
            return configuration;
        }

        @Override
        public List<DiscoveredSource> discover(
                ConnectorExecutionContext context,
                ConnectorConfiguration configuration) {
            return List.of(new DiscoveredSource(
                    "manual-1", PayloadType.MANUAL_FORM, "manual-form:manual-1", Map.of()));
        }

        @Override
        public SourcePayload fetch(ConnectorExecutionContext context, DiscoveredSource source) {
            return new SourcePayload(source, "title=Amazing Grace", "raw-sha256", context.startedAt());
        }

        @Override
        public ConnectorNativeRecord parse(ConnectorExecutionContext context, SourcePayload payload) {
            return new ConnectorNativeRecord(
                    payload,
                    Map.of("title", "Amazing Grace", "artist", "Traditional"),
                    Map.of());
        }

        @Override
        public NormalizedImportCandidate normalize(
                ConnectorExecutionContext context,
                ConnectorNativeRecord nativeRecord) {
            return new NormalizedImportCandidate(
                    descriptor().connectorId(),
                    descriptor().providerName(),
                    descriptor().importMethod(),
                    nativeRecord.payload().source().sourceRecordId(),
                    nativeRecord.payload().source().sourceReference(),
                    LicenseType.PUBLIC_DOMAIN,
                    nativeRecord.payload().retrievedAt(),
                    nativeRecord.payload().rawContentHash(),
                    "normalized-sha256",
                    nativeRecord.fields().get("title"),
                    "amazing grace",
                    nativeRecord.fields().get("artist"),
                    null,
                    "{\"title\":\"Amazing Grace\"}",
                    Map.of());
        }
    }

    private static final class PolicyBlockedProviderAdapter implements ProviderAdapter {

        private int discoverCalls;
        private int fetchCalls;

        @Override
        public ConnectorDescriptor descriptor() {
            return new ConnectorDescriptor(
                    "ultimate-guitar",
                    "Ultimate Guitar",
                    "Policy-blocked placeholder adapter",
                    ImportMethod.API_IMPORT,
                    LegalMode.DISABLED_POLICY_BLOCKED,
                    CredentialRequirement.REQUIRED_OPERATOR_PROVIDED,
                    Set.of(PayloadType.PROVIDER_API_RECORD),
                    new RateLimitPolicy(RateLimitBehavior.PROVIDER_ENFORCED, 10, true),
                    AutomationLevel.AUTHORIZED_API,
                    Set.of(
                            ConnectorCapability.DISCOVER,
                            ConnectorCapability.FETCH,
                            ConnectorCapability.PARSE,
                            ConnectorCapability.NORMALIZE));
        }

        @Override
        public ConnectorConfiguration configure(ConnectorConfiguration configuration) {
            return configuration;
        }

        @Override
        public List<DiscoveredSource> discover(
                ConnectorExecutionContext context,
                ConnectorConfiguration configuration) {
            discoverCalls++;
            throw new AssertionError("discover should not be called for policy-blocked connectors");
        }

        @Override
        public SourcePayload fetch(ConnectorExecutionContext context, DiscoveredSource source) {
            fetchCalls++;
            throw new AssertionError("fetch should not be called for policy-blocked connectors");
        }

        @Override
        public ConnectorNativeRecord parse(ConnectorExecutionContext context, SourcePayload payload) {
            throw new AssertionError("parse should not be called for policy-blocked connectors");
        }

        @Override
        public NormalizedImportCandidate normalize(
                ConnectorExecutionContext context,
                ConnectorNativeRecord nativeRecord) {
            throw new AssertionError("normalize should not be called for policy-blocked connectors");
        }
    }

    private static final class RecordingImportPipeline implements SharedImportPipeline {

        private final List<NormalizedImportCandidate> validatedCandidates = new ArrayList<>();
        private final List<NormalizedImportCandidate> stagedCandidates = new ArrayList<>();
        private boolean approvedCatalogWriteAttempted;

        @Override
        public CandidateValidationResult validate(
                ConnectorExecutionContext context,
                NormalizedImportCandidate candidate) {
            validatedCandidates.add(candidate);
            return CandidateValidationResult.valid(candidate);
        }

        @Override
        public StagedImportCandidate stage(ConnectorExecutionContext context, NormalizedImportCandidate candidate) {
            stagedCandidates.add(candidate);
            return new StagedImportCandidate(
                    context.importBatchId(),
                    context.runId(),
                    candidate.sourceRecordId(),
                    candidate.normalizedTitle(),
                    ImportCandidateStatus.STAGED);
        }
    }
}

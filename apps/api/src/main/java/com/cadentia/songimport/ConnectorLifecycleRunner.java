package com.cadentia.songimport;

import java.util.ArrayList;
import java.util.List;

public final class ConnectorLifecycleRunner {

    private final SharedImportPipeline importPipeline;
    private final ConnectorErrorTranslator errorTranslator;

    public ConnectorLifecycleRunner(SharedImportPipeline importPipeline, ConnectorErrorTranslator errorTranslator) {
        this.importPipeline = ImportConnectorValidation.requireNonNull(importPipeline, "importPipeline");
        this.errorTranslator = ImportConnectorValidation.requireNonNull(errorTranslator, "errorTranslator");
    }

    public ConnectorLifecycleResult run(
            ProviderAdapter providerAdapter,
            ConnectorExecutionContext context,
            ConnectorConfiguration configuration) {
        List<ConnectorLifecycleEvent> events = new ArrayList<>();
        List<StagedImportCandidate> stagedCandidates = new ArrayList<>();
        List<ConnectorFailure> failures = new ArrayList<>();

        ConnectorDescriptor descriptor = providerAdapter.descriptor();
        events.add(ConnectorLifecycleEvent.of(ConnectorLifecycleStage.CONFIGURE, "Connector configuration accepted"));
        if (descriptor.legalMode() == LegalMode.DISABLED_POLICY_BLOCKED) {
            failures.add(new ConnectorFailure(
                    ConnectorLifecycleStage.CONFIGURE,
                    ConnectorErrorCode.POLICY_BLOCKED,
                    "Connector is disabled by policy",
                    false));
            return new ConnectorLifecycleResult(events, stagedCandidates, failures);
        }

        ConnectorConfiguration configured = providerAdapter.configure(configuration);
        List<DiscoveredSource> discoveredSources;
        try {
            discoveredSources = providerAdapter.discover(context, configured);
            events.add(ConnectorLifecycleEvent.of(ConnectorLifecycleStage.DISCOVER, "Source records discovered"));
        } catch (RuntimeException exception) {
            failures.add(errorTranslator.translate(ConnectorLifecycleStage.DISCOVER, exception));
            return new ConnectorLifecycleResult(events, stagedCandidates, failures);
        }

        for (DiscoveredSource source : discoveredSources) {
            processSource(providerAdapter, context, source, events, stagedCandidates, failures);
        }
        return new ConnectorLifecycleResult(events, stagedCandidates, failures);
    }

    private void processSource(
            ProviderAdapter providerAdapter,
            ConnectorExecutionContext context,
            DiscoveredSource source,
            List<ConnectorLifecycleEvent> events,
            List<StagedImportCandidate> stagedCandidates,
            List<ConnectorFailure> failures) {
        SourcePayload payload;
        try {
            payload = providerAdapter.fetch(context, source);
            events.add(ConnectorLifecycleEvent.of(ConnectorLifecycleStage.FETCH, "Source payload fetched"));
        } catch (RuntimeException exception) {
            failures.add(errorTranslator.translate(ConnectorLifecycleStage.FETCH, exception));
            return;
        }

        ConnectorNativeRecord nativeRecord;
        try {
            nativeRecord = providerAdapter.parse(context, payload);
            events.add(ConnectorLifecycleEvent.of(ConnectorLifecycleStage.PARSE, "Source payload parsed"));
        } catch (RuntimeException exception) {
            failures.add(errorTranslator.translate(ConnectorLifecycleStage.PARSE, exception));
            return;
        }

        NormalizedImportCandidate normalizedCandidate;
        try {
            normalizedCandidate = providerAdapter.normalize(context, nativeRecord);
            events.add(ConnectorLifecycleEvent.of(ConnectorLifecycleStage.NORMALIZE, "Candidate normalized"));
        } catch (RuntimeException exception) {
            failures.add(errorTranslator.translate(ConnectorLifecycleStage.NORMALIZE, exception));
            return;
        }

        CandidateValidationResult validationResult = importPipeline.validate(context, normalizedCandidate);
        if (!validationResult.valid()) {
            failures.addAll(validationResult.failures());
            events.add(ConnectorLifecycleEvent.of(ConnectorLifecycleStage.VALIDATE, "Candidate validation failed"));
            return;
        }

        events.add(ConnectorLifecycleEvent.of(ConnectorLifecycleStage.VALIDATE, "Candidate validation passed"));
        try {
            stagedCandidates.add(importPipeline.stage(context, normalizedCandidate));
            events.add(ConnectorLifecycleEvent.of(ConnectorLifecycleStage.STAGE, "Candidate staged for review"));
        } catch (RuntimeException exception) {
            failures.add(errorTranslator.translate(ConnectorLifecycleStage.STAGE, exception));
        }
    }
}

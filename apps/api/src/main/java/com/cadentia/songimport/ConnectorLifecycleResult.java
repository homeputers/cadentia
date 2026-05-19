package com.cadentia.songimport;

import java.util.List;

public record ConnectorLifecycleResult(
        List<ConnectorLifecycleEvent> events,
        List<StagedImportCandidate> stagedCandidates,
        List<ConnectorFailure> failures) {

    public ConnectorLifecycleResult {
        events = List.copyOf(ImportConnectorValidation.requireNonNull(events, "events"));
        stagedCandidates = List.copyOf(ImportConnectorValidation.requireNonNull(
                stagedCandidates, "stagedCandidates"));
        failures = List.copyOf(ImportConnectorValidation.requireNonNull(failures, "failures"));
    }
}

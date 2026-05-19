package com.cadentia.songimport.safe;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.songimport.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class SafeFileConnector implements ProviderAdapter {
    private final String connectorId;
    private final String providerName;
    private final PayloadType payloadType;

    protected SafeFileConnector(String connectorId, String providerName, PayloadType payloadType) {
        this.connectorId = connectorId;
        this.providerName = providerName;
        this.payloadType = payloadType;
    }

    @Override
    public ConnectorDescriptor descriptor() { return new ConnectorDescriptor(connectorId, providerName, providerName, importMethod(), LegalMode.ENABLED,
            CredentialRequirement.NONE, Set.of(payloadType), RateLimitPolicy.notApplicable(), AutomationLevel.MANUAL,
            Set.of(ConnectorCapability.DISCOVER, ConnectorCapability.FETCH, ConnectorCapability.PARSE, ConnectorCapability.NORMALIZE)); }
    protected abstract ImportMethod importMethod();
    protected abstract Map<String, String> parseFields(String rawContent);
    @Override public ConnectorConfiguration configure(ConnectorConfiguration configuration) { return configuration; }
    @Override public List<DiscoveredSource> discover(ConnectorExecutionContext context, ConnectorConfiguration configuration) {
        return List.of(new DiscoveredSource(configuration.sourceIdentifiers().getOrDefault("recordId", configuration.sourceSystem()), payloadType, configuration.sourceIdentifiers().getOrDefault("recordId", configuration.sourceSystem()), configuration.operatorOptions()));
    }
    @Override public SourcePayload fetch(ConnectorExecutionContext context, DiscoveredSource source) {
        String raw = source.metadata().getOrDefault("rawContent", "");
        return new SourcePayload(source, raw, SafeConnectorSupport.sha256(raw), context.startedAt());
    }
    @Override public ConnectorNativeRecord parse(ConnectorExecutionContext context, SourcePayload payload) {
        return new ConnectorNativeRecord(payload, parseFields(payload.rawContent()), Map.of());
    }
    @Override public NormalizedImportCandidate normalize(ConnectorExecutionContext context, ConnectorNativeRecord nativeRecord) {
        String title = nativeRecord.fields().getOrDefault("title", "Untitled");
        String norm = title.trim().toLowerCase();
        return new NormalizedImportCandidate(connectorId, providerName, importMethod(),
                nativeRecord.payload().source().sourceRecordId(), nativeRecord.payload().source().sourceReference(),
                SafeConnectorSupport.parseLicense(nativeRecord.fields().get("license")), nativeRecord.payload().retrievedAt(),
                nativeRecord.payload().rawContentHash(), SafeConnectorSupport.sha256(norm), title, norm,
                nativeRecord.fields().get("artist"), nativeRecord.fields().get("ccli"), nativeRecord.payload().rawContent(),
                nativeRecord.fields().containsKey("notes") ? Map.of("ambiguous_metadata", nativeRecord.fields().get("notes")) : Map.of());
    }
}

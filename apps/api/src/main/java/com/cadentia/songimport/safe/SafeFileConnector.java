package com.cadentia.songimport.safe;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.songimport.AutomationLevel;
import com.cadentia.songimport.ConnectorCapability;
import com.cadentia.songimport.ConnectorConfiguration;
import com.cadentia.songimport.ConnectorDescriptor;
import com.cadentia.songimport.ConnectorExecutionContext;
import com.cadentia.songimport.ConnectorNativeRecord;
import com.cadentia.songimport.CredentialRequirement;
import com.cadentia.songimport.DiscoveredSource;
import com.cadentia.songimport.LegalMode;
import com.cadentia.songimport.NormalizedImportCandidate;
import com.cadentia.songimport.PayloadType;
import com.cadentia.songimport.ProviderAdapter;
import com.cadentia.songimport.RateLimitPolicy;
import com.cadentia.songimport.SourcePayload;
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
    public ConnectorDescriptor descriptor() {
        return new ConnectorDescriptor(
                connectorId,
                providerName,
                providerName,
                importMethod(),
                LegalMode.ENABLED,
                CredentialRequirement.NONE,
                Set.of(payloadType),
                RateLimitPolicy.notApplicable(),
                AutomationLevel.MANUAL,
                Set.of(
                        ConnectorCapability.DISCOVER,
                        ConnectorCapability.FETCH,
                        ConnectorCapability.PARSE,
                        ConnectorCapability.NORMALIZE));
    }

    protected abstract ImportMethod importMethod();

    protected abstract Map<String, String> parseFields(String rawContent);

    @Override
    public ConnectorConfiguration configure(ConnectorConfiguration configuration) {
        return configuration;
    }

    @Override
    public List<DiscoveredSource> discover(
            ConnectorExecutionContext context, ConnectorConfiguration configuration) {
        String recordId =
                configuration.sourceIdentifiers().getOrDefault("recordId", configuration.sourceSystem());
        return List.of(new DiscoveredSource(recordId, payloadType, recordId, configuration.operatorOptions()));
    }

    @Override
    public SourcePayload fetch(ConnectorExecutionContext context, DiscoveredSource source) {
        String raw = source.metadata().getOrDefault("rawContent", "");
        return new SourcePayload(source, raw, SafeConnectorSupport.sha256(raw), context.startedAt());
    }

    @Override
    public ConnectorNativeRecord parse(ConnectorExecutionContext context, SourcePayload payload) {
        return new ConnectorNativeRecord(payload, parseFields(payload.rawContent()), Map.of());
    }

    @Override
    public NormalizedImportCandidate normalize(
            ConnectorExecutionContext context, ConnectorNativeRecord nativeRecord) {
        String title = nativeRecord.fields().getOrDefault("title", "Untitled");
        String normalizedTitle = title.trim().toLowerCase();

        return new NormalizedImportCandidate(
                connectorId,
                providerName,
                importMethod(),
                nativeRecord.payload().source().sourceRecordId(),
                nativeRecord.payload().source().sourceReference(),
                SafeConnectorSupport.parseLicense(nativeRecord.fields().get("license")),
                nativeRecord.payload().retrievedAt(),
                nativeRecord.payload().rawContentHash(),
                SafeConnectorSupport.sha256(normalizedTitle),
                title,
                normalizedTitle,
                nativeRecord.fields().get("artist"),
                nativeRecord.fields().get("ccli"),
                nativeRecord.payload().rawContent(),
                nativeRecord.fields().containsKey("notes")
                        ? Map.of("ambiguous_metadata", nativeRecord.fields().get("notes"))
                        : Map.of());
    }
}

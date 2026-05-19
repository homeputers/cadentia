package com.cadentia.songimport.safe;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.songimport.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ManualEntryConnector implements ProviderAdapter {

    @Override
    public ConnectorDescriptor descriptor() {
        return new ConnectorDescriptor(
                "safe-manual-entry",
                "Manual Entry",
                "First-party manual song entry connector",
                ImportMethod.MANUAL_ENTRY,
                LegalMode.ENABLED,
                CredentialRequirement.NONE,
                Set.of(PayloadType.MANUAL_FORM),
                RateLimitPolicy.notApplicable(),
                AutomationLevel.MANUAL,
                Set.of(ConnectorCapability.DISCOVER, ConnectorCapability.FETCH, ConnectorCapability.PARSE, ConnectorCapability.NORMALIZE));
    }

    @Override public ConnectorConfiguration configure(ConnectorConfiguration configuration) { return configuration; }

    @Override
    public List<DiscoveredSource> discover(ConnectorExecutionContext context, ConnectorConfiguration configuration) {
        return List.of(new DiscoveredSource(configuration.sourceIdentifiers().getOrDefault("recordId", configuration.sourceSystem()), PayloadType.MANUAL_FORM,
                "manual-form:" + configuration.sourceIdentifiers().getOrDefault("recordId", configuration.sourceSystem()), Map.of()));
    }

    @Override
    public SourcePayload fetch(ConnectorExecutionContext context, DiscoveredSource source) {
        String raw = source.metadata().getOrDefault("rawContent", "");
        return new SourcePayload(source, raw, SafeConnectorSupport.sha256(raw), context.startedAt());
    }

    @Override
    public ConnectorNativeRecord parse(ConnectorExecutionContext context, SourcePayload payload) {
        Map<String, String> fields = SimpleKeyValueParsers.parseKeyValueLines(payload.rawContent());
        return new ConnectorNativeRecord(payload, fields, Map.of());
    }

    @Override
    public NormalizedImportCandidate normalize(ConnectorExecutionContext context, ConnectorNativeRecord nativeRecord) {
        String title = nativeRecord.fields().getOrDefault("title", "Untitled");
        String artist = nativeRecord.fields().get("artist");
        String notes = nativeRecord.fields().get("notes");
        String normalized = title.trim().toLowerCase();
        LicenseType licenseType = SafeConnectorSupport.parseLicense(nativeRecord.fields().get("license"));
        return new NormalizedImportCandidate(descriptor().connectorId(), descriptor().providerName(), descriptor().importMethod(),
                nativeRecord.payload().source().sourceRecordId(), nativeRecord.payload().source().sourceReference(), licenseType,
                nativeRecord.payload().retrievedAt(), nativeRecord.payload().rawContentHash(), SafeConnectorSupport.sha256(normalized),
                title, normalized, artist, nativeRecord.fields().get("ccli"), nativeRecord.payload().rawContent(),
                notes == null ? Map.of() : Map.of("manual_notes", notes));
    }
}

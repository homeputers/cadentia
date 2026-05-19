package com.cadentia.songimport.safe;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
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
            ConnectorExecutionContext context, ConnectorConfiguration configuration) {
        String recordId =
                configuration.sourceIdentifiers().getOrDefault("recordId", configuration.sourceSystem());
        return List.of(
                new DiscoveredSource(recordId, PayloadType.MANUAL_FORM, "manual-form:" + recordId, Map.of()));
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
    public NormalizedImportCandidate normalize(
            ConnectorExecutionContext context, ConnectorNativeRecord nativeRecord) {
        String title = nativeRecord.fields().getOrDefault("title", "Untitled");
        String normalizedTitle = title.trim().toLowerCase();
        String notes = nativeRecord.fields().get("notes");
        LicenseType licenseType = SafeConnectorSupport.parseLicense(nativeRecord.fields().get("license"));

        return new NormalizedImportCandidate(
                descriptor().connectorId(),
                descriptor().providerName(),
                descriptor().importMethod(),
                nativeRecord.payload().source().sourceRecordId(),
                nativeRecord.payload().source().sourceReference(),
                licenseType,
                nativeRecord.payload().retrievedAt(),
                nativeRecord.payload().rawContentHash(),
                SafeConnectorSupport.sha256(normalizedTitle),
                title,
                normalizedTitle,
                nativeRecord.fields().get("artist"),
                nativeRecord.fields().get("ccli"),
                nativeRecord.payload().rawContent(),
                notes == null ? Map.of() : Map.of("manual_notes", notes));
    }
}

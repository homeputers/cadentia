package com.cadentia.songimport;

import java.util.List;

public interface ProviderAdapter {

    ConnectorDescriptor descriptor();

    ConnectorConfiguration configure(ConnectorConfiguration configuration);

    List<DiscoveredSource> discover(ConnectorExecutionContext context, ConnectorConfiguration configuration);

    SourcePayload fetch(ConnectorExecutionContext context, DiscoveredSource source);

    ConnectorNativeRecord parse(ConnectorExecutionContext context, SourcePayload payload);

    NormalizedImportCandidate normalize(ConnectorExecutionContext context, ConnectorNativeRecord nativeRecord);
}

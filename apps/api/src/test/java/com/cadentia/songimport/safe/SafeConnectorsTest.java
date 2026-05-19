package com.cadentia.songimport.safe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.songimport.ConnectorExecutionContext;
import com.cadentia.songimport.DiscoveredSource;
import com.cadentia.songimport.PayloadType;
import com.cadentia.songimport.SourcePayload;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafeConnectorsTest {

    @Test
    void connectorImportMethodsMatchTheirFormats() {
        assertThat(new CsvImportConnector().descriptor().importMethod()).isEqualTo(ImportMethod.CSV_IMPORT);
        assertThat(new ChordProImportConnector().descriptor().importMethod()).isEqualTo(ImportMethod.CHORDPRO_IMPORT);
        assertThat(new OpenSongImportConnector().descriptor().importMethod()).isEqualTo(ImportMethod.OPENSONG_IMPORT);
        assertThat(new MarkdownImportConnector().descriptor().importMethod()).isEqualTo(ImportMethod.MARKDOWN_IMPORT);
        assertThat(new ManualEntryConnector().descriptor().importMethod()).isEqualTo(ImportMethod.MANUAL_ENTRY);
    }

    @Test
    void csvConnectorParsesValidFixture() throws Exception {
        Map<String, String> fields = new CsvImportConnector().parse(
                context(), payload(PayloadType.CSV, read("songimport/safe/csv-valid.csv"))).fields();
        assertThat(fields).containsEntry("title", "Amazing Grace");
    }

    @Test
    void csvConnectorRejectsInvalidFixture() {
        assertThatThrownBy(() -> new CsvImportConnector().parse(context(), payload(PayloadType.CSV, "title,artist")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chordProParserValidAndInvalidFixtures() throws Exception {
        assertThat(new ChordProImportConnector().parse(context(), payload(PayloadType.CHORDPRO, read("songimport/safe/chordpro-valid.pro"))).fields())
                .containsEntry("title", "Blessed Be Your Name");
        assertThatThrownBy(() -> new ChordProImportConnector().parse(context(), payload(PayloadType.CHORDPRO, read("songimport/safe/chordpro-invalid.pro"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openSongParserValidAndInvalidFixtures() throws Exception {
        assertThat(new OpenSongImportConnector().parse(context(), payload(PayloadType.OPENSONG_XML, read("songimport/safe/opensong-valid.xml"))).fields())
                .containsEntry("title", "10,000 Reasons");
        assertThatThrownBy(() -> new OpenSongImportConnector().parse(context(), payload(PayloadType.OPENSONG_XML, read("songimport/safe/opensong-invalid.xml"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markdownParserValidAndInvalidFixtures() throws Exception {
        assertThat(new MarkdownImportConnector().parse(context(), payload(PayloadType.MARKDOWN, read("songimport/safe/markdown-valid.md"))).fields())
                .containsEntry("title", "How Great Thou Art");
        assertThatThrownBy(() -> new MarkdownImportConnector().parse(context(), payload(PayloadType.MARKDOWN, read("songimport/safe/markdown-invalid.md"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ConnectorExecutionContext context() {
        return new ConnectorExecutionContext(UUID.randomUUID(), UUID.randomUUID(), "ops@test", Instant.parse("2026-05-19T00:00:00Z"));
    }

    private static SourcePayload payload(PayloadType type, String raw) {
        DiscoveredSource source = new DiscoveredSource("source-1", type, "fixture", Map.of());
        return new SourcePayload(source, raw, SafeConnectorSupport.sha256(raw), Instant.parse("2026-05-19T00:00:00Z"));
    }

    private static String read(String path) throws Exception {
        try (InputStream stream = SafeConnectorsTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing fixture: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.model.LyricsFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LyricsParserRegistry {

    private final List<LyricsParserPlugin> parserPlugins;
    private final ParserCapabilityRegistry capabilityRegistry;
    private final ParserDiagnosticCodebook diagnosticCodebook;

    public LyricsParserRegistry() {
        this(
                List.of(
                new DeterministicLyricsParserPlugin(DeterministicLyricsParser.forFormat(LyricsFormat.PLAIN_TEXT), 100),
                new DeterministicLyricsParserPlugin(DeterministicLyricsParser.forFormat(LyricsFormat.CHORDPRO), 100),
                new DeterministicLyricsParserPlugin(DeterministicLyricsParser.forFormat(LyricsFormat.MARKDOWN), 100)),
                new ParserCapabilityRegistry(),
                new ParserDiagnosticCodebook());
    }

    LyricsParserRegistry(
            List<LyricsParserPlugin> parserPlugins,
            ParserCapabilityRegistry capabilityRegistry,
            ParserDiagnosticCodebook diagnosticCodebook) {
        this.parserPlugins = parserPlugins.stream()
                .sorted(Comparator.comparingInt(LyricsParserPlugin::priority).reversed()
                        .thenComparing(LyricsParserPlugin::parserName)
                        .thenComparing(plugin -> plugin.format().name()))
                .toList();
        this.capabilityRegistry = capabilityRegistry;
        this.diagnosticCodebook = diagnosticCodebook;
    }

    public Optional<LyricsParserPlugin> findParser(LyricsFormat format, String sourceReference) {
        ParserSelectionInput selectionInput = new ParserSelectionInput(format, sourceReference);
        return parserPlugins.stream().filter(plugin -> plugin.supports(selectionInput)).findFirst();
    }

    public LyricsParseResult parse(LyricsFormat format, String sourceReference, String content) {
        return findParser(format, sourceReference)
                .map(parser -> parser.parse(content))
                .orElseGet(() -> unsupportedForFormat(format));
    }

    public LyricsParseResult parse(LyricsFormat format, String content) {
        return parse(format, null, content);
    }

    public ParserCapabilityRegistry capabilityRegistry() {
        return capabilityRegistry;
    }

    public ParserDiagnosticCodebook diagnosticCodebook() {
        return diagnosticCodebook;
    }

    private LyricsParseResult unsupportedForFormat(LyricsFormat format) {
        ParserDiagnosticCode code = diagnosticCodebook.require("PARSER_UNSUPPORTED_FORMAT");
        return LyricsParseResult.unsupported(code.code(), "No deterministic parser is currently implemented for format " + format.storageValue());
    }
}

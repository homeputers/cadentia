package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.model.LyricsFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LyricsParserRegistry {

    private final Map<LyricsFormat, LyricsParser> parsersByFormat;

    public LyricsParserRegistry() {
        this(List.of(
                DeterministicLyricsParser.forFormat(LyricsFormat.PLAIN_TEXT),
                DeterministicLyricsParser.forFormat(LyricsFormat.CHORDPRO),
                DeterministicLyricsParser.forFormat(LyricsFormat.MARKDOWN)));
    }

    LyricsParserRegistry(List<LyricsParser> parsers) {
        Map<LyricsFormat, LyricsParser> mutableParsers = new EnumMap<>(LyricsFormat.class);
        for (LyricsParser parser : parsers) {
            mutableParsers.put(parser.format(), parser);
        }
        parsersByFormat = Map.copyOf(mutableParsers);
    }

    public Optional<LyricsParser> findParser(LyricsFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }
        return Optional.ofNullable(parsersByFormat.get(format));
    }

    public LyricsParseResult parse(LyricsFormat format, String content) {
        return findParser(format)
                .map(parser -> parser.parse(content))
                .orElseGet(() -> LyricsParseResult.unsupported(
                        "No deterministic parser is currently implemented for format " + format.storageValue()));
    }
}

package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.model.LyricsFormat;

public record DeterministicLyricsParserPlugin(LyricsParser parser, int priority) implements LyricsParserPlugin {

    public DeterministicLyricsParserPlugin {
        if (parser == null) {
            throw new IllegalArgumentException("parser is required");
        }
    }

    @Override
    public boolean supports(ParserSelectionInput selectionInput) {
        return selectionInput != null && parser.format() == selectionInput.format();
    }

    @Override
    public LyricsFormat format() {
        return parser.format();
    }

    @Override
    public String parserName() {
        return parser.parserName();
    }

    @Override
    public String parserVersion() {
        return parser.parserVersion();
    }

    @Override
    public LyricsParseResult parse(String content) {
        return parser.parse(content);
    }
}

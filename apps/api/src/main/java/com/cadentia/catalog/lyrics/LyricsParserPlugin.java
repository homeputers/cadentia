package com.cadentia.catalog.lyrics;

public interface LyricsParserPlugin extends LyricsParser {

    int priority();

    boolean supports(ParserSelectionInput selectionInput);
}

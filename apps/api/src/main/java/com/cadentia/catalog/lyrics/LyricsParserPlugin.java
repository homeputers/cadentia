package com.cadentia.catalog.lyrics;

public interface LyricsParserPlugin extends LyricsParser {

    int priority();

    boolean supports(ParserSelectionInput selectionInput);

    default java.util.List<String> declaredCapabilities() {
        return java.util.List.of("SECTION_STRUCTURE");
    }
}

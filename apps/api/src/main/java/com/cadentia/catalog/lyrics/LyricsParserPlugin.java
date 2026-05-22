package com.cadentia.catalog.lyrics;

import java.util.List;

public interface LyricsParserPlugin extends LyricsParser {

    int priority();

    boolean supports(ParserSelectionInput selectionInput);

    default List<String> declaredCapabilities() {
        return List.of("SECTION_STRUCTURE");
    }
}

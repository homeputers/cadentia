package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.model.LyricsFormat;

public record ParserSelectionInput(LyricsFormat format, String sourceReference) {

    public ParserSelectionInput {
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }
    }
}

package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.model.LyricsFormat;

public record ParserCapability(String capability, LyricsFormat format, String description) {

    public ParserCapability {
        capability = ParserOutputValidation.requireText(capability, "capability");
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }
        description = ParserOutputValidation.requireText(description, "description");
    }
}

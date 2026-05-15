package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.model.LyricsFormat;

public interface LyricsParser {

    LyricsFormat format();

    String parserName();

    String parserVersion();

    LyricsParseResult parse(String content);
}

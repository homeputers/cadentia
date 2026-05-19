package com.cadentia.songimport.safe;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.songimport.*;
import java.util.Map;

public final class MarkdownImportConnector extends SafeFileConnector {
    public MarkdownImportConnector() { super("safe-markdown-import", "Local Markdown Import", PayloadType.MARKDOWN); }
    @Override protected Map<String, String> parseFields(String rawContent) { return MarkdownSongParser.parse(rawContent); }
    @Override protected ImportMethod importMethod() { return ImportMethod.CSV_IMPORT; }
}

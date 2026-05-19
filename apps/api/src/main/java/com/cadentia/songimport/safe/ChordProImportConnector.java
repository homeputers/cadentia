package com.cadentia.songimport.safe;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.songimport.*;
import java.util.Map;

public final class ChordProImportConnector extends SafeFileConnector {
    public ChordProImportConnector() { super("safe-chordpro-import", "ChordPro Import", PayloadType.CHORDPRO); }
    @Override protected Map<String, String> parseFields(String rawContent) { return ChordProParser.parse(rawContent); }
    @Override protected ImportMethod importMethod() { return ImportMethod.CSV_IMPORT; }
}

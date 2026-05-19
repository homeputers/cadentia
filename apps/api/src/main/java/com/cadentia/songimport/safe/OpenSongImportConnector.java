package com.cadentia.songimport.safe;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.songimport.*;
import java.util.Map;

public final class OpenSongImportConnector extends SafeFileConnector {
    public OpenSongImportConnector() { super("safe-opensong-import", "OpenSong Import", PayloadType.OPENSONG_XML); }
    @Override protected Map<String, String> parseFields(String rawContent) { return OpenSongXmlParser.parse(rawContent); }
    @Override protected ImportMethod importMethod() { return ImportMethod.CSV_IMPORT; }
}

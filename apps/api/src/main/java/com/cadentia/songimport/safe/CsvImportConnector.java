package com.cadentia.songimport.safe;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.songimport.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CsvImportConnector extends SafeFileConnector {
    public CsvImportConnector() { super("safe-csv-import", "CSV Import", PayloadType.CSV); }
    @Override protected Map<String, String> parseFields(String rawContent) { return SimpleKeyValueParsers.parseCsvRecord(rawContent); }
    @Override protected ImportMethod importMethod() { return ImportMethod.CSV_IMPORT; }
}

package com.cadentia.songimport.safe;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class SimpleKeyValueParsers {

    private SimpleKeyValueParsers() {}

    static Map<String, String> parseKeyValueLines(String content) {
        Map<String, String> parsed = new LinkedHashMap<>();
        Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .forEach(line -> {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        parsed.put(parts[0].trim().toLowerCase(), parts[1].trim());
                    }
                });
        return parsed;
    }

    static Map<String, String> parseCsvRecord(String content) {
        String[] lines = content.split("\\R");
        if (lines.length < 2) {
            throw new IllegalArgumentException("CSV payload must include header and one row");
        }
        String[] headers = lines[0].split(",");
        String[] values = lines[1].split(",", -1);
        Map<String, String> parsed = new LinkedHashMap<>();
        for (int index = 0; index < headers.length; index++) {
            String header = headers[index].trim().toLowerCase();
            String value = index < values.length ? values[index].trim() : "";
            parsed.put(header, value);
        }
        return parsed;
    }
}

package com.cadentia.songimport.safe;

import java.util.LinkedHashMap;
import java.util.Map;

final class ChordProParser {

    private ChordProParser() {}

    static Map<String, String> parse(String content) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("{title:")) {
                parsed.put("title", trimmed.substring(7, trimmed.length() - 1).trim());
            }
            if (trimmed.startsWith("{artist:")) {
                parsed.put("artist", trimmed.substring(8, trimmed.length() - 1).trim());
            }
            if (trimmed.startsWith("{ccli:")) {
                parsed.put("ccli", trimmed.substring(6, trimmed.length() - 1).trim());
            }
        }

        if (!parsed.containsKey("title")) {
            throw new IllegalArgumentException("ChordPro title is required");
        }
        return parsed;
    }
}

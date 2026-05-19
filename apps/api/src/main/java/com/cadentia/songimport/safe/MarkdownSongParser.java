package com.cadentia.songimport.safe;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class MarkdownSongParser {

    private MarkdownSongParser() {}

    static Map<String, String> parse(String content) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String line : content.split("\\R")) {
            String normalizedLine = line.toLowerCase(Locale.ROOT);
            if (line.startsWith("# ")) {
                parsed.put("title", line.substring(2).trim());
            }
            if (normalizedLine.startsWith("artist:")) {
                parsed.put("artist", line.substring(7).trim());
            }
            if (normalizedLine.startsWith("license:")) {
                parsed.put("license", line.substring(8).trim());
            }
        }

        if (!parsed.containsKey("title")) {
            throw new IllegalArgumentException("Markdown title is required");
        }
        return parsed;
    }
}

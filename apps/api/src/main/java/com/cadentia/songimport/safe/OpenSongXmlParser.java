package com.cadentia.songimport.safe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OpenSongXmlParser {
    private static final Pattern TAG = Pattern.compile("<(?<tag>title|author|ccli)>(?<value>.*?)</\\1>", Pattern.DOTALL);
    private OpenSongXmlParser() {}
    static Map<String, String> parse(String content) {
        Map<String, String> parsed = new LinkedHashMap<>();
        Matcher matcher = TAG.matcher(content);
        while (matcher.find()) {
            String tag = matcher.group("tag");
            String value = matcher.group("value").trim();
            if (tag.equals("author")) { parsed.put("artist", value); } else { parsed.put(tag, value); }
        }
        if (!parsed.containsKey("title")) { throw new IllegalArgumentException("OpenSong title is required"); }
        return parsed;
    }
}

package com.cadentia.catalog.lyrics;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ParserCapabilityRegistry {

    private static final String REGISTRY_VERSION = "adr-009-capabilities-v1";

    private final List<ParserCapability> capabilities;

    public ParserCapabilityRegistry() {
        this.capabilities = List.of(
                new ParserCapability("SECTION_STRUCTURE", com.cadentia.catalog.model.LyricsFormat.PLAIN_TEXT, "Section extraction"),
                new ParserCapability("SECTION_STRUCTURE", com.cadentia.catalog.model.LyricsFormat.CHORDPRO, "Section extraction"),
                new ParserCapability("SECTION_STRUCTURE", com.cadentia.catalog.model.LyricsFormat.MARKDOWN, "Section extraction"),
                new ParserCapability("CHORD_DETECTION", com.cadentia.catalog.model.LyricsFormat.CHORDPRO, "Chord token extraction"),
                new ParserCapability("CHORD_DETECTION", com.cadentia.catalog.model.LyricsFormat.MARKDOWN, "Chord token extraction"));
    }

    public String version() {
        return REGISTRY_VERSION;
    }

    public boolean supports(String capability, ParserSelectionInput input) {
        return capabilities.stream()
                .anyMatch(item -> item.capability().equals(capability) && item.format() == input.format());
    }

    public List<ParserCapability> all() {
        return capabilities;
    }
}

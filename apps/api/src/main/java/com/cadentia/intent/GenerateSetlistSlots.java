package com.cadentia.intent;

import java.util.List;

public record GenerateSetlistSlots(
        String verseText,
        List<String> scriptureReferences,
        List<String> themeHints,
        Counts counts,
        IntentKeyPolicy keyPolicy,
        IntentTempoPolicy tempoPolicy,
        String language,
        String energyArc,
        List<String> excludedSongs,
        String serviceMoment) {

    public GenerateSetlistSlots {
        scriptureReferences = List.copyOf(scriptureReferences);
        themeHints = List.copyOf(themeHints);
        excludedSongs = List.copyOf(excludedSongs);
    }
}

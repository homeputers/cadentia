package com.cadentia.reng.scoring;

import java.util.List;

public record ScoringRequest(
        String verseText,
        List<String> themeHints,
        int praiseCount,
        int worshipCount,
        KeyPolicy keyPolicy,
        TempoPolicy tempoPolicy,
        String energyArc,
        String language,
        List<String> excludedSongIds,
        DefaultsApplied defaultsApplied) {

    public ScoringRequest {
        themeHints = themeHints == null ? List.of() : List.copyOf(themeHints);
        excludedSongIds = excludedSongIds == null ? List.of() : List.copyOf(excludedSongIds);
    }

    public record KeyPolicy(boolean preferSameKey, boolean allowRelativeMajorMinor, int maxKeyCenters) {}

    public record TempoPolicy(int maxJumpBpm) {}

    public record DefaultsApplied(
            boolean countsDefaulted,
            boolean keyPolicyDefaulted,
            boolean tempoPolicyDefaulted,
            boolean languageDefaulted) {}
}

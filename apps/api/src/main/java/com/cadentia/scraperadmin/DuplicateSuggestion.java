package com.cadentia.scraperadmin;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DuplicateSuggestion(
        UUID songId,
        BigDecimal score,
        List<MatchSignal> signals,
        boolean automaticMergeAllowed) {

    public DuplicateSuggestion {
        if (songId == null) {
            throw new NullPointerException("songId is required");
        }
        if (score == null) {
            throw new NullPointerException("score is required");
        }
        signals = List.copyOf(signals == null ? List.of() : signals);
        automaticMergeAllowed = false;
    }
}

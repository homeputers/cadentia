package com.cadentia.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GenerateSetlistRequest(
        @NotBlank String verseText,
        List<String> themeHints,
        @Valid @NotNull Counts counts,
        @Valid @NotNull KeyPolicy keyPolicy,
        @Valid @NotNull TempoPolicy tempoPolicy) {

    public record Counts(@Min(0) @Max(25) int praise, @Min(0) @Max(25) int worship) {
    }

    public record KeyPolicy(boolean preferSameKey, boolean allowRelativeMajorMinor, @Min(1) @Max(12) int maxKeyCenters) {
    }

    public record TempoPolicy(@Min(1) @Max(60) int maxJumpBpm) {
    }
}

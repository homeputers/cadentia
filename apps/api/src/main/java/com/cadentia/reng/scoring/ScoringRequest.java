package com.cadentia.reng.scoring;

import com.cadentia.reng.scoring.TeamSuitabilityModels.ExplicitTeamConstraints;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamContextReference;
import java.util.List;

public record ScoringRequest(
        String verseText,
        List<String> scriptureReferences,
        List<String> themeHints,
        int praiseCount,
        int worshipCount,
        KeyPolicy keyPolicy,
        TempoPolicy tempoPolicy,
        String energyArc,
        String language,
        List<String> excludedSongIds,
        boolean includeAdminExplanations,
        DefaultsApplied defaultsApplied,
        TeamContextReference teamContextReference,
        ExplicitTeamConstraints explicitTeamConstraints) {

    public ScoringRequest {
        scriptureReferences = scriptureReferences == null ? List.of() : List.copyOf(scriptureReferences);
        themeHints = themeHints == null ? List.of() : List.copyOf(themeHints);
        excludedSongIds = excludedSongIds == null ? List.of() : List.copyOf(excludedSongIds);
    }

    public ScoringRequest(
            String verseText,
            List<String> themeHints,
            int praiseCount,
            int worshipCount,
            KeyPolicy keyPolicy,
            TempoPolicy tempoPolicy,
            String energyArc,
            String language,
            List<String> excludedSongIds,
            boolean includeAdminExplanations,
            DefaultsApplied defaultsApplied) {
        this(verseText, themeHints, praiseCount, worshipCount, keyPolicy, tempoPolicy, energyArc, language,
                excludedSongIds, includeAdminExplanations, defaultsApplied, null, null);
    }

    public ScoringRequest(
            String verseText,
            List<String> themeHints,
            int praiseCount,
            int worshipCount,
            KeyPolicy keyPolicy,
            TempoPolicy tempoPolicy,
            String energyArc,
            String language,
            List<String> excludedSongIds,
            boolean includeAdminExplanations,
            DefaultsApplied defaultsApplied,
            TeamContextReference teamContextReference,
            ExplicitTeamConstraints explicitTeamConstraints) {
        this(verseText, List.of(), themeHints, praiseCount, worshipCount, keyPolicy, tempoPolicy, energyArc, language,
                excludedSongIds, includeAdminExplanations, defaultsApplied, teamContextReference,
                explicitTeamConstraints);
    }

    public record KeyPolicy(boolean preferSameKey, boolean allowRelativeMajorMinor, int maxKeyCenters) {}

    public record TempoPolicy(int maxJumpBpm) {}

    public record DefaultsApplied(
            boolean countsDefaulted,
            boolean keyPolicyDefaulted,
            boolean tempoPolicyDefaulted,
            boolean languageDefaulted) {}
}

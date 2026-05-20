package com.cadentia.reng.scoring;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.TempoPolicy;
import java.util.List;

public class ScoringRequestFactory {

    private static final int DEFAULT_PRAISE_COUNT = 10;
    private static final int DEFAULT_WORSHIP_COUNT = 5;
    private static final ScoringRequest.KeyPolicy DEFAULT_KEY_POLICY =
            new ScoringRequest.KeyPolicy(true, true, 2);
    private static final ScoringRequest.TempoPolicy DEFAULT_TEMPO_POLICY =
            new ScoringRequest.TempoPolicy(12);
    private static final String DEFAULT_LANGUAGE = "en";

    public ScoringRequest fromValidatedRequest(GenerateSetlistRequest request) {
        SetlistCounts counts = request.getCounts();
        KeyPolicy keyPolicy = request.getKeyPolicy();
        TempoPolicy tempoPolicy = request.getTempoPolicy();

        boolean countsDefaulted = counts == null;
        boolean keyPolicyDefaulted = keyPolicy == null;
        boolean tempoPolicyDefaulted = tempoPolicy == null;
        boolean languageDefaulted = request.getLanguage() == null || request.getLanguage().isBlank();

        return new ScoringRequest(
                request.getVerseText(),
                request.getThemeHints() == null ? List.of() : request.getThemeHints(),
                countsDefaulted ? DEFAULT_PRAISE_COUNT : counts.getPraise(),
                countsDefaulted ? DEFAULT_WORSHIP_COUNT : counts.getWorship(),
                keyPolicyDefaulted
                        ? DEFAULT_KEY_POLICY
                        : new ScoringRequest.KeyPolicy(
                                Boolean.TRUE.equals(keyPolicy.getPreferSameKey()),
                                Boolean.TRUE.equals(keyPolicy.getAllowRelativeMajorMinor()),
                                keyPolicy.getMaxKeyCenters()),
                tempoPolicyDefaulted
                        ? DEFAULT_TEMPO_POLICY
                        : new ScoringRequest.TempoPolicy(tempoPolicy.getMaxJumpBpm()),
                request.getEnergyArc() == null ? null : request.getEnergyArc().getValue(),
                languageDefaulted ? DEFAULT_LANGUAGE : request.getLanguage(),
                request.getExcludedSongs() == null ? List.of() : request.getExcludedSongs(),
                new ScoringRequest.DefaultsApplied(
                        countsDefaulted,
                        keyPolicyDefaulted,
                        tempoPolicyDefaulted,
                        languageDefaulted));
    }
}

package com.cadentia.reng;

import com.cadentia.catalog.transposition.MusicalKey;

public record KeyPolicyEvaluation(
        RecommendableArrangement candidate,
        MusicalKey baseKey,
        MusicalKey targetKey,
        TranspositionType transpositionType,
        int score,
        String explanation) {

    public boolean usesDynamicTransposition() {
        return transpositionType == TranspositionType.DYNAMIC_TRANSPOSITION;
    }
}

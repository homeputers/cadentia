package com.cadentia.reng.scoring;

import com.cadentia.reng.RecommendableArrangement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransitionExplanationFactory {

    public List<RecommendationExplanationFact> build(
            RecommendableArrangement from,
            RecommendableArrangement to,
            TransitionScore transition,
            ScoringRequest request) {
        if (transition == null) {
            return List.of();
        }

        RecommendationExplanationSubject subject = new RecommendationExplanationSubject(
                "transition",
                from.arrangementId() + "->" + to.arrangementId(),
                from.arrangementId().toString(),
                to.arrangementId().toString());

        List<RecommendationExplanationFact> facts = new ArrayList<>();
        for (ScoringComponentScore score : transition.components()) {
            switch (score.componentCode()) {
                case TransitionScorer.KEY_SAME -> addFact(facts, "SAME_KEY_TRANSITION", "info", subject,
                        "transition.same_key", Map.of("fromKey", from.musicalKey(), "toKey", to.musicalKey()), score, "keyMovement");
                case TransitionScorer.KEY_RELATIVE -> addFact(facts, "RELATIVE_KEY_TRANSITION", "info", subject,
                        "transition.relative_key",
                        Map.of("fromKey", from.musicalKey(), "toKey", to.musicalKey(), "allowRelativeMajorMinor", request.keyPolicy().allowRelativeMajorMinor()),
                        score,
                        "keyMovement");
                case TransitionScorer.KEY_CLOSE -> addFact(facts, "CLOSE_KEY_TRANSITION", "info", subject,
                        "transition.close_key", Map.of("fromKey", from.musicalKey(), "toKey", to.musicalKey()), score, "keyMovement");
                case TransitionScorer.KEY_MODULATION -> addFact(
                        facts,
                        "MODULATION_PENALTY",
                        score.rawScore() < 0.0d ? "warning" : "info",
                        subject,
                        "transition.modulation_penalty",
                        Map.of("penalty", score.rawScore()),
                        score,
                        "keyMovement");
                case TransitionScorer.BPM_JUMP -> addFact(
                        facts,
                        "TEMPO_POLICY_OK",
                        score.rawScore() >= 1.0d ? "info" : "warning",
                        subject,
                        "transition.tempo_policy",
                        Map.of("fromBpm", from.bpm(), "toBpm", to.bpm(), "maxJumpBpm", request.tempoPolicy().maxJumpBpm()),
                        score,
                        "tempo");
                case TransitionScorer.METER_MATCH -> addFact(
                        facts,
                        "METER_COMPATIBLE",
                        score.rawScore() >= 1.0d ? "info" : "warning",
                        subject,
                        "transition.meter_compatibility",
                        Map.of("fromMeter", from.timeSignature(), "toMeter", to.timeSignature()),
                        score,
                        "meter");
                case TransitionScorer.ENERGY_CONTINUITY -> addFact(
                        facts,
                        "ENERGY_ARC_MATCH",
                        score.rawScore() >= 0.8d ? "info" : "warning",
                        subject,
                        "transition.energy_continuity",
                        Map.of("fromEnergy", from.energy(), "toEnergy", to.energy()),
                        score,
                        "energy");
                default -> {
                    // no-op
                }
            }
        }

        return List.copyOf(facts);
    }

    private static void addFact(
            List<RecommendationExplanationFact> facts,
            String code,
            String severity,
            RecommendationExplanationSubject subject,
            String templateKey,
            Map<String, Object> values,
            ScoringComponentScore score,
            String field) {
        facts.add(new RecommendationExplanationFact(
                code,
                severity,
                "transition",
                subject,
                templateKey,
                values,
                List.of(new RecommendationExplanationEvidence("transition", score.componentCode(), field, null)),
                score.weightedContribution()));
    }
}

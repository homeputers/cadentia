package com.cadentia.reng.scoring;

import java.util.List;
import java.util.UUID;

public record TransitionExplanationEntry(
        String id,
        int sequence,
        TransitionEndpoint source,
        TransitionEndpoint target,
        List<String> reasonCodes,
        List<ScoringComponentScore> scoreComponents,
        double totalScore,
        KeyChange keyChange,
        TempoChange tempoChange,
        MeterChange meterChange,
        EnergyMovement energyMovement,
        ArrangementCompatibility arrangementCompatibility,
        List<RecommendationExplanationFact> facts,
        List<TransitionWarning> warnings,
        List<RecommendationSongExplanation.UiDisplayHint> uiDisplayHints) {

    public TransitionExplanationEntry {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        scoreComponents = scoreComponents == null ? List.of() : List.copyOf(scoreComponents);
        facts = facts == null ? List.of() : List.copyOf(facts);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        uiDisplayHints = uiDisplayHints == null ? List.of() : List.copyOf(uiDisplayHints);
    }

    public record TransitionEndpoint(
            String itemId,
            UUID arrangementId,
            UUID songId,
            int position) {}

    public record KeyChange(
            String fromKey,
            String toKey,
            String fromMode,
            String toMode,
            boolean sameKey,
            boolean relativeMajorMinor,
            boolean closeKey,
            int maxKeyCenters) {}

    public record TempoChange(
            int fromBpm,
            int toBpm,
            int jumpBpm,
            int maxJumpBpm,
            boolean withinPolicy) {}

    public record MeterChange(
            String fromMeter,
            String toMeter,
            boolean compatible) {}

    public record EnergyMovement(
            int fromEnergy,
            int toEnergy,
            int delta) {}

    public record ArrangementCompatibility(
            Boolean compatible,
            Double parserConfidence,
            List<RecommendationExplanationEvidence> evidence) {

        public ArrangementCompatibility {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record TransitionWarning(
            String code,
            String severity,
            List<String> affectedFields) {

        public TransitionWarning {
            affectedFields = affectedFields == null ? List.of() : List.copyOf(affectedFields);
        }
    }
}

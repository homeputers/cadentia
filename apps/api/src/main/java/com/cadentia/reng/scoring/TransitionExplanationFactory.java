package com.cadentia.reng.scoring;

import com.cadentia.reng.ArrangementTransitionMetadata;
import com.cadentia.reng.RecommendableArrangement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransitionExplanationFactory {

    public List<RecommendationExplanationFact> build(
            RecommendableArrangement from,
            RecommendableArrangement to,
            TransitionScore transition,
            ScoringRequest request) {
        return buildFacts(from, to, transition, request, null);
    }

    public TransitionExplanationEntry buildEntry(
            OrderedSetItem sourceItem,
            OrderedSetItem targetItem,
            RecommendableArrangement from,
            RecommendableArrangement to,
            TransitionScore transition,
            ScoringRequest request) {
        if (transition == null) {
            return null;
        }

        List<TransitionExplanationEntry.TransitionWarning> warnings = warnings(from, to, transition, request);
        List<RecommendationExplanationFact> facts = buildFacts(from, to, transition, request, warnings);
        List<String> reasonCodes = facts.stream().map(RecommendationExplanationFact::code).distinct().toList();

        return new TransitionExplanationEntry(
                "transition-" + sourceItem.position() + "-" + targetItem.position(),
                sourceItem.position(),
                endpoint(sourceItem),
                endpoint(targetItem),
                reasonCodes,
                transition.components(),
                transition.totalScore(),
                keyChange(from, to, request),
                tempoChange(from, to, request),
                meterChange(from, to),
                energyMovement(from, to),
                arrangementCompatibility(from, to),
                facts,
                warnings,
                uiDisplayHints(facts, warnings));
    }

    private List<RecommendationExplanationFact> buildFacts(
            RecommendableArrangement from,
            RecommendableArrangement to,
            TransitionScore transition,
            ScoringRequest request,
            List<TransitionExplanationEntry.TransitionWarning> warnings) {
        RecommendationExplanationSubject subject = new RecommendationExplanationSubject(
                "transition",
                from.arrangementId() + "->" + to.arrangementId(),
                from.arrangementId().toString(),
                to.arrangementId().toString());

        List<RecommendationExplanationFact> facts = new ArrayList<>();
        for (ScoringComponentScore score : transition.components()) {
            switch (score.componentCode()) {
                case TransitionScorer.KEY_SAME -> {
                    if (hasKeyMetadata(from, to) && score.rawScore() > 0.0d) {
                        addFact(facts, "SAME_KEY_TRANSITION", "info", subject,
                                "transition.same_key", keyValues(from, to), score, "keyMovement", null);
                    }
                }
                case TransitionScorer.KEY_RELATIVE -> {
                    if (hasKeyMetadata(from, to) && score.rawScore() > 0.0d) {
                        Map<String, Object> values = keyValues(from, to);
                        values.put("allowRelativeMajorMinor", request.keyPolicy().allowRelativeMajorMinor());
                        addFact(facts, "RELATIVE_KEY_TRANSITION", "info", subject,
                                "transition.relative_key", values, score, "keyMovement", null);
                    }
                }
                case TransitionScorer.KEY_CLOSE -> {
                    if (hasKeyMetadata(from, to) && score.rawScore() > 0.0d) {
                        addFact(facts, "CLOSE_KEY_TRANSITION", "info", subject,
                                "transition.close_key", keyValues(from, to), score, "keyMovement", null);
                    }
                }
                case TransitionScorer.KEY_MODULATION -> {
                    if (hasKeyMetadata(from, to) && score.rawScore() < 0.0d) {
                        addFact(facts, "MODULATION_PENALTY", "warning", subject,
                                "transition.modulation_penalty", Map.of("penalty", score.rawScore()), score, "keyMovement", null);
                    }
                }
                case TransitionScorer.BPM_JUMP -> {
                    if (hasTempoMetadata(from, to)) {
                        Map<String, Object> values = new LinkedHashMap<>();
                        values.put("fromBpm", from.bpm());
                        values.put("toBpm", to.bpm());
                        values.put("maxJumpBpm", request.tempoPolicy().maxJumpBpm());
                        addFact(facts, "TEMPO_POLICY_OK", score.rawScore() >= 1.0d ? "info" : "warning", subject,
                                "transition.tempo_policy", values, score, "tempo", null);
                        if (score.rawScore() < 1.0d) {
                            Map<String, Object> tradeoffValues = new LinkedHashMap<>(values);
                            tradeoffValues.put("jumpBpm", Math.abs(from.bpm() - to.bpm()));
                            tradeoffValues.put("penalty", score.rawScore() - 1.0d);
                            addFact(facts, "TEMPO_TRADEOFF_ACCEPTED", "warning", subject,
                                    "transition.tempo_tradeoff_accepted", tradeoffValues, score, "tempo", null);
                        }
                    }
                }
                case TransitionScorer.METER_MATCH -> {
                    if (hasMeterMetadata(from, to)) {
                        addFact(facts, "METER_COMPATIBLE", score.rawScore() >= 1.0d ? "info" : "warning", subject,
                                "transition.meter_compatibility", Map.of("fromMeter", from.timeSignature(), "toMeter", to.timeSignature()), score, "meter", null);
                    }
                }
                case TransitionScorer.ENERGY_CONTINUITY -> {
                    if (hasEnergyMetadata(from, to)) {
                        addFact(facts, "ENERGY_ARC_MATCH", score.rawScore() >= 0.8d ? "info" : "warning", subject,
                                "transition.energy_continuity", Map.of("fromEnergy", from.energy(), "toEnergy", to.energy()), score, "energy", null);
                    }
                }
                default -> {
                    // no-op
                }
            }
        }

        if (hasArrangementCompatibility(from, to)) {
            ArrangementTransitionMetadata metadata = from.transitionMetadata();
            addFact(facts, "ARRANGEMENT_COMPATIBLE", Boolean.TRUE.equals(metadata.compatibleWithAdjacentArrangements()) ? "info" : "warning", subject,
                    "transition.arrangement_compatibility", Map.of("compatible", Boolean.TRUE.equals(metadata.compatibleWithAdjacentArrangements()),
                            "parserConfidence", metadata.parserConfidence()), null, "arrangementCompatibility", compatibilityEvidence(metadata));
        }
        if (warnings != null && warnings.stream().anyMatch(warning -> "TRANSITION_METADATA_MISSING".equals(warning.code()))) {
            addFact(facts, "TRANSITION_METADATA_MISSING", "warning", subject,
                    "transition.metadata_missing", Map.of("missingFields", String.join(",", missingFields(from, to))), null, "metadata", null);
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
            String field,
            List<RecommendationExplanationEvidence> evidence) {
        List<RecommendationExplanationEvidence> factEvidence = evidence == null
                ? List.of(new RecommendationExplanationEvidence("transition", score == null ? code : score.componentCode(), field, null))
                : evidence;
        facts.add(new RecommendationExplanationFact(
                code,
                severity,
                "transition",
                subject,
                templateKey,
                values,
                factEvidence,
                score == null ? null : score.weightedContribution()));
    }

    private static TransitionExplanationEntry.TransitionEndpoint endpoint(OrderedSetItem item) {
        return new TransitionExplanationEntry.TransitionEndpoint(
                "item-" + item.position(), item.arrangementId(), item.songId(), item.position());
    }

    private static TransitionExplanationEntry.KeyChange keyChange(
            RecommendableArrangement from,
            RecommendableArrangement to,
            ScoringRequest request) {
        if (!hasKeyMetadata(from, to)) {
            return null;
        }
        return new TransitionExplanationEntry.KeyChange(
                from.musicalKey(),
                to.musicalKey(),
                from.keyMode().name(),
                to.keyMode().name(),
                componentRaw(from, to, request, TransitionScorer.KEY_SAME) > 0.0d,
                componentRaw(from, to, request, TransitionScorer.KEY_RELATIVE) > 0.0d,
                componentRaw(from, to, request, TransitionScorer.KEY_CLOSE) > 0.0d,
                request.keyPolicy().maxKeyCenters());
    }

    private static TransitionExplanationEntry.TempoChange tempoChange(
            RecommendableArrangement from,
            RecommendableArrangement to,
            ScoringRequest request) {
        if (!hasTempoMetadata(from, to)) {
            return null;
        }
        int jump = Math.abs(from.bpm() - to.bpm());
        return new TransitionExplanationEntry.TempoChange(
                from.bpm(), to.bpm(), jump, request.tempoPolicy().maxJumpBpm(), jump <= request.tempoPolicy().maxJumpBpm());
    }

    private static TransitionExplanationEntry.MeterChange meterChange(RecommendableArrangement from, RecommendableArrangement to) {
        if (!hasMeterMetadata(from, to)) {
            return null;
        }
        return new TransitionExplanationEntry.MeterChange(
                from.timeSignature(), to.timeSignature(), from.timeSignature().trim().equalsIgnoreCase(to.timeSignature().trim()));
    }

    private static TransitionExplanationEntry.EnergyMovement energyMovement(RecommendableArrangement from, RecommendableArrangement to) {
        if (!hasEnergyMetadata(from, to)) {
            return null;
        }
        return new TransitionExplanationEntry.EnergyMovement(from.energy(), to.energy(), to.energy() - from.energy());
    }

    private static TransitionExplanationEntry.ArrangementCompatibility arrangementCompatibility(
            RecommendableArrangement from,
            RecommendableArrangement to) {
        if (!hasArrangementCompatibility(from, to)) {
            return null;
        }
        ArrangementTransitionMetadata metadata = from.transitionMetadata();
        return new TransitionExplanationEntry.ArrangementCompatibility(
                metadata.compatibleWithAdjacentArrangements(),
                metadata.parserConfidence(),
                compatibilityEvidence(metadata));
    }

    private static List<RecommendationExplanationEvidence> compatibilityEvidence(ArrangementTransitionMetadata metadata) {
        return List.of(new RecommendationExplanationEvidence(
                "parser",
                metadata.evidenceRef(),
                "arrangementCompatibility",
                metadata.parserConfidence()));
    }

    private static List<TransitionExplanationEntry.TransitionWarning> warnings(
            RecommendableArrangement from,
            RecommendableArrangement to,
            TransitionScore transition,
            ScoringRequest request) {
        List<TransitionExplanationEntry.TransitionWarning> warnings = new ArrayList<>();
        List<String> missingFields = missingFields(from, to);
        if (!missingFields.isEmpty()) {
            warnings.add(new TransitionExplanationEntry.TransitionWarning("TRANSITION_METADATA_MISSING", "warning", missingFields));
        }
        if (hasTempoMetadata(from, to) && Math.abs(from.bpm() - to.bpm()) > request.tempoPolicy().maxJumpBpm()) {
            warnings.add(new TransitionExplanationEntry.TransitionWarning("TEMPO_TRADEOFF_ACCEPTED", "warning", List.of("tempo")));
        }
        if (transition.components().stream().anyMatch(score -> TransitionScorer.KEY_MODULATION.equals(score.componentCode()) && score.rawScore() < 0.0d)) {
            warnings.add(new TransitionExplanationEntry.TransitionWarning("MODULATION_PENALTY", "warning", List.of("keyMovement")));
        }
        return List.copyOf(warnings);
    }

    private static List<RecommendationSongExplanation.UiDisplayHint> uiDisplayHints(
            List<RecommendationExplanationFact> facts,
            List<TransitionExplanationEntry.TransitionWarning> warnings) {
        List<String> reasonCodes = facts.stream().map(RecommendationExplanationFact::code).distinct().toList();
        String severity = warnings == null || warnings.isEmpty() ? "info" : "warning";
        return List.of(new RecommendationSongExplanation.UiDisplayHint("transitions", severity, "transition.summary", reasonCodes));
    }

    private static Map<String, Object> keyValues(RecommendableArrangement from, RecommendableArrangement to) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fromKey", from.musicalKey());
        values.put("toKey", to.musicalKey());
        return values;
    }

    private static double componentRaw(
            RecommendableArrangement from,
            RecommendableArrangement to,
            ScoringRequest request,
            String code) {
        TransitionScore score = new TransitionScorer().score(from, to, request, new ScoringProfile("explain", Map.of(), List.of()));
        return score.components().stream()
                .filter(component -> code.equals(component.componentCode()))
                .mapToDouble(ScoringComponentScore::rawScore)
                .findFirst()
                .orElse(0.0d);
    }

    private static List<String> missingFields(RecommendableArrangement from, RecommendableArrangement to) {
        List<String> missing = new ArrayList<>();
        if (!hasKeyMetadata(from, to)) {
            missing.add("key");
        }
        if (!hasTempoMetadata(from, to)) {
            missing.add("tempo");
        }
        if (!hasMeterMetadata(from, to)) {
            missing.add("meter");
        }
        if (!hasEnergyMetadata(from, to)) {
            missing.add("energy");
        }
        return List.copyOf(missing);
    }

    private static boolean hasKeyMetadata(RecommendableArrangement from, RecommendableArrangement to) {
        return !blank(from.musicalKey()) && !blank(to.musicalKey()) && from.keyMode() != null && to.keyMode() != null;
    }

    private static boolean hasTempoMetadata(RecommendableArrangement from, RecommendableArrangement to) {
        return from.bpm() > 0 && to.bpm() > 0;
    }

    private static boolean hasMeterMetadata(RecommendableArrangement from, RecommendableArrangement to) {
        return !blank(from.timeSignature()) && !blank(to.timeSignature());
    }

    private static boolean hasEnergyMetadata(RecommendableArrangement from, RecommendableArrangement to) {
        return from.energy() > 0 && to.energy() > 0;
    }

    private static boolean hasArrangementCompatibility(RecommendableArrangement from, RecommendableArrangement to) {
        ArrangementTransitionMetadata metadata = from.transitionMetadata();
        return metadata != null
                && metadata.compatibleWithAdjacentArrangements() != null
                && metadata.parserConfidence() != null
                && !blank(metadata.evidenceRef())
                && to.transitionMetadata() != null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

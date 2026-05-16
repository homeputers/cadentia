package com.cadentia.reng;

import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.transposition.MusicalKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class KeyPolicyEvaluator {

    private static final Map<String, Integer> PITCH_CLASSES = Map.ofEntries(
            Map.entry("C", 0),
            Map.entry("C#", 1),
            Map.entry("Db", 1),
            Map.entry("D", 2),
            Map.entry("D#", 3),
            Map.entry("Eb", 3),
            Map.entry("E", 4),
            Map.entry("F", 5),
            Map.entry("F#", 6),
            Map.entry("Gb", 6),
            Map.entry("G", 7),
            Map.entry("G#", 8),
            Map.entry("Ab", 8),
            Map.entry("A", 9),
            Map.entry("A#", 10),
            Map.entry("Bb", 10),
            Map.entry("B", 11));
    private static final String[] SHARP_SPELLINGS = {
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    public List<KeyPolicyEvaluation> evaluate(
            RecommendableArrangement candidate,
            RecommendationKeyPolicy policy,
            List<MusicalKey> activeKeyCenters) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate is required");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy is required");
        }
        List<MusicalKey> centers = activeKeyCenters == null ? List.of() : List.copyOf(activeKeyCenters);
        MusicalKey baseKey = baseKey(candidate);
        Map<String, KeyPolicyEvaluation> evaluations = new LinkedHashMap<>();

        evaluateStoredArrangementKey(candidate, policy, centers, baseKey)
                .ifPresent(evaluation -> evaluations.put(keyFor(evaluation), evaluation));

        if (policy.allowDynamicTransposition()) {
            for (MusicalKey targetKey : targetKeysForActiveCenters(baseKey, centers, policy)) {
                if (!sameKey(baseKey, targetKey)) {
                    KeyPolicyEvaluation evaluation = dynamicEvaluation(candidate, policy, baseKey, targetKey);
                    evaluations.putIfAbsent(keyFor(evaluation), evaluation);
                }
            }
        }

        return evaluations.values().stream()
                .sorted(Comparator.comparingInt(KeyPolicyEvaluation::score).reversed()
                        .thenComparing(evaluation -> display(evaluation.targetKey()))
                        .thenComparing(evaluation -> evaluation.candidate().arrangementId().toString()))
                .toList();
    }

    private Optional<KeyPolicyEvaluation> evaluateStoredArrangementKey(
            RecommendableArrangement candidate,
            RecommendationKeyPolicy policy,
            List<MusicalKey> centers,
            MusicalKey baseKey) {
        if (centers.isEmpty()) {
            return Optional.of(new KeyPolicyEvaluation(
                    candidate,
                    baseKey,
                    baseKey,
                    TranspositionType.STORED_ARRANGEMENT_KEY,
                    100,
                    "Stored arrangement key " + display(baseKey) + " initializes the first key center."));
        }

        Optional<MusicalKey> exactCenter = centers.stream()
                .filter(center -> sameKey(center, baseKey))
                .findFirst();
        if (exactCenter.isPresent()) {
            return Optional.of(new KeyPolicyEvaluation(
                    candidate,
                    baseKey,
                    baseKey,
                    TranspositionType.STORED_ARRANGEMENT_KEY,
                    policy.preferSameKey() ? 100 : 90,
                    "Stored arrangement key " + display(baseKey) + " matches an active key center."));
        }

        Optional<MusicalKey> relativeCenter = centers.stream()
                .filter(center -> policy.allowRelativeMajorMinor() && areRelativeMajorMinor(center, baseKey))
                .findFirst();
        if (relativeCenter.isPresent()) {
            return Optional.of(new KeyPolicyEvaluation(
                    candidate,
                    baseKey,
                    baseKey,
                    TranspositionType.STORED_ARRANGEMENT_KEY,
                    85,
                    "Stored arrangement key " + display(baseKey)
                            + " is relative major/minor compatible with active key center "
                            + display(relativeCenter.get()) + "."));
        }

        if (centers.size() < policy.maxKeyCenters()) {
            return Optional.of(new KeyPolicyEvaluation(
                    candidate,
                    baseKey,
                    baseKey,
                    TranspositionType.STORED_ARRANGEMENT_KEY,
                    70,
                    "Stored arrangement key " + display(baseKey) + " adds a permitted key center ("
                            + (centers.size() + 1) + " of " + policy.maxKeyCenters() + ")."));
        }

        return Optional.empty();
    }

    private List<MusicalKey> targetKeysForActiveCenters(
            MusicalKey baseKey,
            List<MusicalKey> centers,
            RecommendationKeyPolicy policy) {
        List<MusicalKey> targetKeys = new ArrayList<>();
        for (MusicalKey center : centers) {
            if (center.mode() == baseKey.mode()) {
                targetKeys.add(center);
            }
            if (policy.allowRelativeMajorMinor()) {
                MusicalKey relativeKey = relativeKeyWithMode(center, baseKey.mode());
                if (relativeKey != null) {
                    targetKeys.add(relativeKey);
                }
            }
        }
        return targetKeys;
    }

    private KeyPolicyEvaluation dynamicEvaluation(
            RecommendableArrangement candidate,
            RecommendationKeyPolicy policy,
            MusicalKey baseKey,
            MusicalKey targetKey) {
        int score;
        String relationship;
        if (targetKey.mode() == baseKey.mode()) {
            score = policy.preferSameKey() ? 95 : 88;
            relationship = "matches an active key center";
        } else {
            score = 80;
            relationship = "matches a relative major/minor target for an active key center";
        }
        return new KeyPolicyEvaluation(
                candidate,
                baseKey,
                targetKey,
                TranspositionType.DYNAMIC_TRANSPOSITION,
                score,
                "Target key " + display(targetKey) + " is a dynamic transposition from stored arrangement key "
                        + display(baseKey) + " and " + relationship + ".");
    }

    private MusicalKey baseKey(RecommendableArrangement candidate) {
        if (candidate.musicalKey() == null || candidate.musicalKey().isBlank()) {
            throw new IllegalArgumentException("candidate musicalKey is required");
        }
        if (candidate.keyMode() == null
                || candidate.keyMode() == KeyMode.UNKNOWN
                || candidate.keyMode() == KeyMode.MODAL) {
            throw new IllegalArgumentException("candidate keyMode must be MAJOR or MINOR");
        }
        if (!PITCH_CLASSES.containsKey(candidate.musicalKey())) {
            throw new IllegalArgumentException("candidate musicalKey " + candidate.musicalKey() + " is not supported");
        }
        return new MusicalKey(candidate.musicalKey(), candidate.keyMode());
    }

    private boolean sameKey(MusicalKey left, MusicalKey right) {
        return left.mode() == right.mode() && pitchClass(left) == pitchClass(right);
    }

    private boolean areRelativeMajorMinor(MusicalKey left, MusicalKey right) {
        if (left.mode() == right.mode()) {
            return false;
        }
        if (left.mode() == KeyMode.MAJOR && right.mode() == KeyMode.MINOR) {
            return Math.floorMod(pitchClass(left) - 3, 12) == pitchClass(right);
        }
        if (left.mode() == KeyMode.MINOR && right.mode() == KeyMode.MAJOR) {
            return Math.floorMod(pitchClass(left) + 3, 12) == pitchClass(right);
        }
        return false;
    }

    private MusicalKey relativeKeyWithMode(MusicalKey center, KeyMode mode) {
        if (center.mode() == KeyMode.MAJOR && mode == KeyMode.MINOR) {
            return new MusicalKey(SHARP_SPELLINGS[Math.floorMod(pitchClass(center) - 3, 12)], KeyMode.MINOR);
        }
        if (center.mode() == KeyMode.MINOR && mode == KeyMode.MAJOR) {
            return new MusicalKey(SHARP_SPELLINGS[Math.floorMod(pitchClass(center) + 3, 12)], KeyMode.MAJOR);
        }
        return null;
    }

    private int pitchClass(MusicalKey key) {
        Integer pitchClass = PITCH_CLASSES.get(key.tonic());
        if (pitchClass == null) {
            throw new IllegalArgumentException("key tonic " + key.tonic() + " is not supported");
        }
        return pitchClass;
    }

    private String display(MusicalKey key) {
        return key.tonic() + " " + key.mode().name().toLowerCase();
    }

    private String keyFor(KeyPolicyEvaluation evaluation) {
        return display(evaluation.targetKey()) + ":" + evaluation.transpositionType();
    }
}

package com.cadentia.reng.scoring;

import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.RecommendableArrangement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TransitionScorer {

    public static final String KEY_SAME = "transition_key_same";
    public static final String KEY_RELATIVE = "transition_key_relative";
    public static final String KEY_CLOSE = "transition_key_close";
    public static final String KEY_MODULATION = "transition_key_modulation";
    public static final String BPM_JUMP = "transition_bpm_jump";
    public static final String METER_MATCH = "transition_meter_match";
    public static final String ENERGY_CONTINUITY = "transition_energy_continuity";

    public TransitionScore score(
            RecommendableArrangement from,
            RecommendableArrangement to,
            ScoringRequest request,
            ScoringProfile profile) {
        List<ScoringComponentScore> components = new ArrayList<>();

        KeyDistance keyDistance = keyDistance(from, to, request.keyPolicy().allowRelativeMajorMinor());
        components.add(component(KEY_SAME, keyDistance.same ? 1.0d : 0.0d, profile.componentWeights()));
        components.add(component(KEY_RELATIVE, keyDistance.relative ? 1.0d : 0.0d, profile.componentWeights()));
        components.add(component(KEY_CLOSE, keyDistance.close ? 1.0d : 0.0d, profile.componentWeights()));
        components.add(component(KEY_MODULATION, keyDistance.modulationPenalty, profile.componentWeights()));
        components.add(component(BPM_JUMP, bpmJumpScore(from.bpm(), to.bpm(), request.tempoPolicy().maxJumpBpm()), profile.componentWeights()));
        components.add(component(METER_MATCH, meterScore(from.timeSignature(), to.timeSignature()), profile.componentWeights()));
        components.add(component(ENERGY_CONTINUITY, energyContinuity(from.energy(), to.energy()), profile.componentWeights()));

        double total = components.stream().mapToDouble(ScoringComponentScore::weightedContribution).sum();
        return new TransitionScore(from.arrangementId(), to.arrangementId(), components, total);
    }

    private static ScoringComponentScore component(String code, double raw, Map<String, Double> weights) {
        double weight = weights.getOrDefault(code, 0.0d);
        return new ScoringComponentScore(code, raw, raw * weight);
    }

    private static double bpmJumpScore(int fromBpm, int toBpm, int maxJumpBpm) {
        if (fromBpm <= 0 || toBpm <= 0 || maxJumpBpm <= 0) {
            return 0.5d;
        }
        int jump = Math.abs(fromBpm - toBpm);
        if (jump <= maxJumpBpm) {
            return 1.0d;
        }
        return Math.max(0.0d, 1.0d - ((double) (jump - maxJumpBpm) / maxJumpBpm));
    }

    private static double meterScore(String fromMeter, String toMeter) {
        if (blank(fromMeter) || blank(toMeter)) {
            return 0.5d;
        }
        return normalize(fromMeter).equals(normalize(toMeter)) ? 1.0d : 0.0d;
    }

    private static double energyContinuity(int fromEnergy, int toEnergy) {
        if (fromEnergy <= 0 || toEnergy <= 0) {
            return 0.5d;
        }
        int jump = Math.abs(fromEnergy - toEnergy);
        return Math.max(0.0d, 1.0d - (jump / 100.0d));
    }

    private static KeyDistance keyDistance(RecommendableArrangement from, RecommendableArrangement to, boolean allowRelative) {
        Integer fromPitch = pitchClass(from.musicalKey());
        Integer toPitch = pitchClass(to.musicalKey());
        if (fromPitch == null || toPitch == null) {
            return new KeyDistance(false, false, false, -0.2d);
        }

        boolean sameMode = from.keyMode() == to.keyMode();
        if (fromPitch.equals(toPitch) && sameMode) {
            return new KeyDistance(true, false, true, 0.0d);
        }

        boolean relative = allowRelative && isRelative(from, to);
        if (relative) {
            return new KeyDistance(false, true, true, 0.0d);
        }

        int diff = Math.abs(fromPitch - toPitch);
        int circle = Math.min(diff, 12 - diff);
        boolean close = circle <= 2;
        double modulationPenalty = close ? -0.2d : -1.0d;
        return new KeyDistance(false, false, close, modulationPenalty);
    }

    private static boolean isRelative(RecommendableArrangement from, RecommendableArrangement to) {
        if (from.keyMode() == null || to.keyMode() == null || from.keyMode() == to.keyMode()) {
            return false;
        }
        Integer fromPitch = pitchClass(from.musicalKey());
        Integer toPitch = pitchClass(to.musicalKey());
        if (fromPitch == null || toPitch == null) {
            return false;
        }
        return (from.keyMode() == KeyMode.MAJOR && ((fromPitch + 9) % 12) == toPitch)
                || (from.keyMode() == KeyMode.MINOR && ((fromPitch + 3) % 12) == toPitch);
    }

    private static Integer pitchClass(String value) {
        if (blank(value)) {
            return null;
        }
        return switch (normalize(value)) {
            case "c", "b#" -> 0;
            case "c#", "db" -> 1;
            case "d" -> 2;
            case "d#", "eb" -> 3;
            case "e", "fb" -> 4;
            case "f", "e#" -> 5;
            case "f#", "gb" -> 6;
            case "g" -> 7;
            case "g#", "ab" -> 8;
            case "a" -> 9;
            case "a#", "bb" -> 10;
            case "b", "cb" -> 11;
            default -> null;
        };
    }

    private static boolean blank(String value) {return value == null || value.isBlank();}
    private static String normalize(String value) {return value.trim().toLowerCase(Locale.ROOT);}

    private record KeyDistance(boolean same, boolean relative, boolean close, double modulationPenalty) {}
}

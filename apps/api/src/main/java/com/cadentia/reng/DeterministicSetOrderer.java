package com.cadentia.reng;

import com.cadentia.reng.scoring.CandidateFeatureScorer;
import com.cadentia.reng.scoring.OrderedSetItem;
import com.cadentia.reng.scoring.OrderedSetResponse;
import com.cadentia.reng.scoring.ScoringProfile;
import com.cadentia.reng.scoring.ScoringRequest;
import com.cadentia.reng.scoring.TransitionScore;
import com.cadentia.reng.scoring.TransitionScorer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DeterministicSetOrderer implements SetOrderer {

    private final TransitionScorer transitionScorer;

    public DeterministicSetOrderer() {
        this(new TransitionScorer());
    }

    DeterministicSetOrderer(TransitionScorer transitionScorer) {
        this.transitionScorer = transitionScorer;
    }

    @Override
    public OrderedSetResponse order(
            List<CandidateFeatureScorer.CandidateFeatureScore> candidateScores,
            ScoringRequest request,
            ScoringProfile profile,
            String candidateSnapshotVersion) {
        int targetSize = request.praiseCount() + request.worshipCount();
        List<CandidateFeatureScorer.CandidateFeatureScore> sorted = candidateScores.stream()
                .sorted(Comparator
                        .comparingDouble(CandidateFeatureScorer.CandidateFeatureScore::totalScore)
                        .reversed()
                        .thenComparing(score -> score.candidate().songId())
                        .thenComparing(score -> score.candidate().arrangementId()))
                .toList();

        List<CandidateFeatureScorer.CandidateFeatureScore> selected = new ArrayList<>();
        Set<String> keyCenters = new HashSet<>();
        for (CandidateFeatureScorer.CandidateFeatureScore score : sorted) {
            if (selected.size() >= targetSize) {
                break;
            }
            String key = normalize(score.candidate().musicalKey());
            if (!key.isBlank() && keyCenters.size() >= request.keyPolicy().maxKeyCenters() && !keyCenters.contains(key)) {
                continue;
            }
            selected.add(score);
            if (!key.isBlank()) {
                keyCenters.add(key);
            }
        }

        List<OrderedSetItem> items = new ArrayList<>();
        double totalScore = 0.0d;
        CandidateFeatureScorer.CandidateFeatureScore previous = null;

        for (int index = 0; index < selected.size(); index++) {
            CandidateFeatureScorer.CandidateFeatureScore current = selected.get(index);
            TransitionScore transition = null;
            double itemScore = current.totalScore();
            if (previous != null) {
                transition = transitionScorer.score(previous.candidate(), current.candidate(), request, profile);
                itemScore += transition.totalScore();
            }
            totalScore += itemScore;
            items.add(new OrderedSetItem(
                    current.candidate().arrangementId(),
                    current.candidate().songId(),
                    index + 1,
                    current.componentScores(),
                    current.totalScore(),
                    transition));
            previous = current;
        }

        return OrderedSetResponse.of(profile, candidateSnapshotVersion, items, totalScore);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

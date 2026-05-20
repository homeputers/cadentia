package com.cadentia.reng;

import com.cadentia.catalog.model.ApprovalStatus;
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

    static final double SCORE_TIE_EPSILON = 0.0001d;

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
                .sorted(deterministicCandidateComparator())
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

    private static Comparator<CandidateFeatureScorer.CandidateFeatureScore> deterministicCandidateComparator() {
        return (left, right) -> {
            int totalScoreComparison = compareDescendingWithTolerance(left.totalScore(), right.totalScore());
            if (totalScoreComparison != 0) {
                return totalScoreComparison;
            }

            int approvalConfidenceComparison = Integer.compare(
                    approvalConfidence(right.candidate().approvalGateSummary()),
                    approvalConfidence(left.candidate().approvalGateSummary()));
            if (approvalConfidenceComparison != 0) {
                return approvalConfidenceComparison;
            }

            int titleComparison = normalize(left.candidate().title()).compareTo(normalize(right.candidate().title()));
            if (titleComparison != 0) {
                return titleComparison;
            }

            int songIdComparison = left.candidate().songId().compareTo(right.candidate().songId());
            if (songIdComparison != 0) {
                return songIdComparison;
            }

            return left.candidate().arrangementId().compareTo(right.candidate().arrangementId());
        };
    }

    private static int compareDescendingWithTolerance(double left, double right) {
        if (Math.abs(left - right) < SCORE_TIE_EPSILON) {
            return 0;
        }
        return Double.compare(right, left);
    }

    private static int approvalConfidence(ApprovalGateSummary summary) {
        if (summary == null) {
            return 0;
        }
        int confidence = 0;
        confidence += approvalWeight(summary.songDoctrinalStatus());
        confidence += approvalWeight(summary.songEditorialStatus());
        confidence += approvalWeight(summary.songLicensingStatus());
        confidence += approvalWeight(summary.arrangementMusicalStatus());
        confidence += approvalWeight(summary.arrangementEditorialStatus());
        confidence += approvalWeight(summary.lyricsDoctrinalStatus());
        confidence += approvalWeight(summary.lyricsEditorialStatus());
        confidence += approvalWeight(summary.lyricsLicensingStatus());
        return confidence;
    }

    private static int approvalWeight(ApprovalStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case APPROVED -> 3;
            case PENDING -> 2;
            case NEEDS_REVIEW -> 1;
            case REJECTED -> 0;
        };
    }
}

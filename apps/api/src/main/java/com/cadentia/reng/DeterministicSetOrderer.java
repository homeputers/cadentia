package com.cadentia.reng;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.reng.scoring.CandidateFeatureScorer;
import com.cadentia.reng.scoring.EnergyArcEvaluation;
import com.cadentia.reng.scoring.EnergyArcEvaluator;
import com.cadentia.reng.scoring.ItemExplanationFactory;
import com.cadentia.reng.scoring.OrderedSetItem;
import com.cadentia.reng.scoring.OrderedSetResponse;
import com.cadentia.reng.scoring.RecommendationExplanationEvidence;
import com.cadentia.reng.scoring.RecommendationExplanationFact;
import com.cadentia.reng.scoring.RecommendationExplanationSubject;
import com.cadentia.reng.scoring.RecommendationSongExplanation;
import com.cadentia.reng.scoring.ScoringProfile;
import com.cadentia.reng.scoring.ScoringRequest;
import com.cadentia.reng.scoring.SetExplanationFactory;
import com.cadentia.reng.scoring.TransitionExplanationEntry;
import com.cadentia.reng.scoring.TransitionExplanationFactory;
import com.cadentia.reng.scoring.TransitionScore;
import com.cadentia.reng.scoring.TransitionScorer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DeterministicSetOrderer implements SetOrderer {

    static final double SCORE_TIE_EPSILON = 0.0001d;

    private final TransitionScorer transitionScorer;
    private final ItemExplanationFactory itemExplanationFactory;
    private final TransitionExplanationFactory transitionExplanationFactory;
    private final SetExplanationFactory setExplanationFactory;
    private final EnergyArcEvaluator energyArcEvaluator;

    public DeterministicSetOrderer() {
        this(
                new TransitionScorer(),
                new ItemExplanationFactory(),
                new TransitionExplanationFactory(),
                new SetExplanationFactory(),
                new EnergyArcEvaluator());
    }

    DeterministicSetOrderer(TransitionScorer transitionScorer) {
        this(
                transitionScorer,
                new ItemExplanationFactory(),
                new TransitionExplanationFactory(),
                new SetExplanationFactory(),
                new EnergyArcEvaluator());
    }

    DeterministicSetOrderer(
            TransitionScorer transitionScorer,
            ItemExplanationFactory itemExplanationFactory,
            TransitionExplanationFactory transitionExplanationFactory,
            SetExplanationFactory setExplanationFactory,
            EnergyArcEvaluator energyArcEvaluator) {
        this.transitionScorer = transitionScorer;
        this.itemExplanationFactory = itemExplanationFactory;
        this.transitionExplanationFactory = transitionExplanationFactory;
        this.setExplanationFactory = setExplanationFactory;
        this.energyArcEvaluator = energyArcEvaluator;
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
        List<TransitionExplanationEntry> adjacentTransitionExplanations = new ArrayList<>();
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
            List<RecommendationExplanationFact> explanationFacts = new ArrayList<>(
                    itemExplanationFactory.build(current.candidate(), request, current.componentScores()));
            if (previous != null && transition != null) {
                explanationFacts.addAll(transitionExplanationFactory.build(
                        previous.candidate(), current.candidate(), transition, request));
            }

            RecommendationSongExplanation songExplanation = itemExplanationFactory.buildSongExplanation(
                    current.candidate(), request, current.componentScores(), index + 1);
            OrderedSetItem item = new OrderedSetItem(
                    current.candidate().arrangementId(),
                    current.candidate().songId(),
                    index + 1,
                    explanationFacts,
                    current.componentScores(),
                    current.totalScore(),
                    transition,
                    songExplanation);
            items.add(item);
            if (previous != null && transition != null) {
                adjacentTransitionExplanations.add(transitionExplanationFactory.buildEntry(
                        items.get(index - 1), item, previous.candidate(), current.candidate(), transition, request));
            }
            previous = current;
        }

        EnergyArcEvaluation energyArcEvaluation = energyArcEvaluator.evaluate(
                selected.stream().map(CandidateFeatureScorer.CandidateFeatureScore::candidate).toList(),
                request,
                profile);
        List<RecommendationExplanationFact> setExplanationFacts = setExplanationFactory.build(
                request, selected, sorted, items, energyArcEvaluation);
        List<RecommendationExplanationFact> adminFacts = request.includeAdminExplanations()
                ? buildAdminExclusionFacts(sorted, selected, request)
                : List.of();

        return new OrderedSetResponse(
                profile.version(),
                candidateSnapshotVersion,
                items,
                setExplanationFacts,
                adminFacts,
                adjacentTransitionExplanations,
                profile.lifecycle(),
                profile.deterministicTieBreakOrder(),
                totalScore);
    }

    private List<RecommendationExplanationFact> buildAdminExclusionFacts(
            List<CandidateFeatureScorer.CandidateFeatureScore> sorted,
            List<CandidateFeatureScorer.CandidateFeatureScore> selected,
            ScoringRequest request) {
        Set<java.util.UUID> selectedIds = selected.stream().map(score -> score.candidate().arrangementId()).collect(java.util.stream.Collectors.toSet());
        List<RecommendationExplanationFact> facts = new ArrayList<>();
        int targetSize = request.praiseCount() + request.worshipCount();
        for (CandidateFeatureScorer.CandidateFeatureScore score : sorted) {
            if (selectedIds.contains(score.candidate().arrangementId())) {
                continue;
            }
            String reason = selected.size() >= targetSize ? "EXCLUDED_QUOTA_FILLED" : "EXCLUDED_WEAKER_SCORE";
            facts.add(new RecommendationExplanationFact(
                    reason,
                    "info",
                    "candidate_exclusion",
                    new RecommendationExplanationSubject("candidate", score.candidate().arrangementId().toString(), null, null),
                    reason.equals("EXCLUDED_QUOTA_FILLED") ? "candidate_exclusion.quota_filled" : "candidate_exclusion.weaker_score",
                    Map.of("candidateTitle", score.candidate().title(), "candidateScore", score.totalScore()),
                    List.of(new RecommendationExplanationEvidence("score", "candidate", score.candidate().arrangementId().toString(), score.totalScore())),
                    null));
        }
        return facts;
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

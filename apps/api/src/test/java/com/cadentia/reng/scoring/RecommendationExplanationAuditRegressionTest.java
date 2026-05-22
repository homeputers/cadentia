package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.TagType;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.DeterministicSetOrderer;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RecommendationExplanationAuditRegressionTest {

    private final DeterministicSetOrderer orderer = new DeterministicSetOrderer();

    @Test
    void everyGeneratedExplanationFactIncludesTraceableEvidence() {
        CandidateFeatureScorer scorer = new CandidateFeatureScorer();
        ScoringRequest request = request();
        ScoringProfile profile = scoringProfile();

        RecommendableArrangement alpha = candidate("Alpha", "C", 112, 72, List.of(
                new RecommendationTag(UUID.randomUUID(), TagType.THEME, "Holiness", "holiness"),
                new RecommendationTag(UUID.randomUUID(), TagType.SCRIPTURE, "Psalm 24", "psalm-24")));
        RecommendableArrangement beta = candidate("Beta", "G", 132, 58, List.of(
                new RecommendationTag(UUID.randomUUID(), TagType.THEME, "Faith", "faith")));

        List<CandidateFeatureScorer.CandidateFeatureScore> scores = scorer.scoreCandidates(List.of(alpha, beta), request, profile);

        OrderedSetResponse ordered = orderer.order(scores, request, profile, "snapshot-v1");

        List<RecommendationExplanationFact> allFacts = java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(
                        ordered.items().stream().flatMap(item -> item.explanationFacts().stream()),
                        ordered.setExplanationFacts().stream()),
                ordered.adminCandidateExplanationFacts().stream()).toList();

        assertThat(allFacts).isNotEmpty();
        assertThat(allFacts).allSatisfy(fact -> {
            assertThat(fact.evidence()).as("Fact %s must contain evidence", fact.code()).isNotEmpty();
            assertThat(fact.evidence()).allSatisfy(evidence -> {
                assertThat(evidence.type()).as("Evidence type for %s", fact.code()).isNotBlank();
                assertThat(evidence.ref()).as("Evidence ref for %s", fact.code()).isNotBlank();
            });
        });
    }

    @Test
    void majorScoringComponentsHaveExplanationCoverageOrDocumentedInternalOnlyStatus() {
        CandidateFeatureScorer scorer = new CandidateFeatureScorer();
        ScoringRequest request = request();
        ScoringProfile profile = scoringProfile();

        RecommendableArrangement alpha = candidate("Alpha", "C", 112, 72, List.of(
                new RecommendationTag(UUID.randomUUID(), TagType.THEME, "Holiness", "holiness"),
                new RecommendationTag(UUID.randomUUID(), TagType.SCRIPTURE, "Psalm 24", "psalm-24")));
        CandidateFeatureScorer.CandidateFeatureScore score = scorer.scoreCandidate(alpha, request, profile);

        OrderedSetResponse ordered = orderer.order(List.of(score), request, profile, "snapshot-v1");

        List<String> explanationCodes = ordered.items().stream()
                .flatMap(item -> item.explanationFacts().stream())
                .map(RecommendationExplanationFact::code)
                .collect(Collectors.toList());

        assertThat(explanationCodes).contains("ROLE_FIT", "THEME_MATCH", "APPROVAL_ELIGIBLE");
        assertThat(internalOnlyScoringComponents()).containsExactlyInAnyOrder(
                CandidateFeatureScorer.MUSICAL_FIT,
                CandidateFeatureScorer.ENERGY_FIT);
    }

    @Test
    void explanationGenerationIsDeterministicAndDoesNotAffectSelection() {
        CandidateFeatureScorer scorer = new CandidateFeatureScorer();
        ScoringRequest request = request();
        ScoringProfile profile = scoringProfile();

        RecommendableArrangement alpha = candidate("Alpha", "C", 112, 72, List.of());
        RecommendableArrangement beta = candidate("Beta", "G", 132, 58, List.of());
        RecommendableArrangement gamma = candidate("Gamma", "D", 120, 61, List.of());

        List<CandidateFeatureScorer.CandidateFeatureScore> scores = scorer.scoreCandidates(List.of(beta, alpha, gamma), request, profile);

        OrderedSetResponse withoutAdmin = orderer.order(scores, request, profile, "snapshot-v1");
        OrderedSetResponse secondRun = orderer.order(scores, request, profile, "snapshot-v1");
        ScoringRequest adminRequest = new ScoringRequest(
                request.verseText(),
                request.themeHints(),
                request.praiseCount(),
                request.worshipCount(),
                request.keyPolicy(),
                request.tempoPolicy(),
                request.energyArc(),
                request.language(),
                request.excludedSongIds(),
                true,
                request.defaultsApplied());
        OrderedSetResponse withAdmin = orderer.order(scores, adminRequest, profile, "snapshot-v1");

        assertThat(withoutAdmin.items()).extracting(OrderedSetItem::arrangementId)
                .containsExactlyElementsOf(secondRun.items().stream().map(OrderedSetItem::arrangementId).toList());
        assertThat(withoutAdmin.items()).extracting(OrderedSetItem::arrangementId)
                .containsExactlyElementsOf(withAdmin.items().stream().map(OrderedSetItem::arrangementId).toList());
        assertThat(withAdmin.adminCandidateExplanationFacts()).isNotEmpty();
    }

    private static List<String> internalOnlyScoringComponents() {
        return List.of(CandidateFeatureScorer.MUSICAL_FIT, CandidateFeatureScorer.ENERGY_FIT);
    }

    private static ScoringRequest request() {
        return new ScoringRequest(
                "Psalm 24",
                List.of("holiness"),
                1,
                1,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                "build",
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile scoringProfile() {
        return new ScoringProfile(
                "v1",
                Map.of(
                        CandidateFeatureScorer.THEME_MATCH, 0.3,
                        CandidateFeatureScorer.SCRIPTURE_MATCH, 0.2,
                        CandidateFeatureScorer.ROLE_FIT, 0.2,
                        CandidateFeatureScorer.MUSICAL_FIT, 0.1,
                        CandidateFeatureScorer.ENERGY_FIT, 0.1,
                        CandidateFeatureScorer.METADATA_CONFIDENCE, 0.1,
                        TransitionScorer.BPM_JUMP, 0.05,
                        TransitionScorer.METER_MATCH, 0.05),
                List.of("total_score", "song_id", "arrangement_id"));
    }

    private static RecommendableArrangement candidate(String title, String key, int bpm, int energy, List<RecommendationTag> tags) {
        return new RecommendableArrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                title,
                "en",
                key,
                KeyMode.MAJOR,
                bpm,
                "4/4",
                energy,
                List.of("praise"),
                tags,
                tags,
                approvedSummary());
    }

    private static ApprovalGateSummary approvedSummary() {
        return new ApprovalGateSummary(
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED);
    }
}

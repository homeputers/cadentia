package com.cadentia.reng;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.TagType;
import com.cadentia.reng.scoring.CandidateFeatureScorer;
import com.cadentia.reng.scoring.OrderedSetResponse;
import com.cadentia.reng.scoring.RecommendationExplanationFact;
import com.cadentia.reng.scoring.ScoringComponentScore;
import com.cadentia.reng.scoring.ScoringProfile;
import com.cadentia.reng.scoring.ScoringRequest;
import com.cadentia.reng.scoring.TransitionScorer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicSetOrdererTest {

    private final DeterministicSetOrderer orderer = new DeterministicSetOrderer(new TransitionScorer());

    @Test
    void orderIsDeterministicForIdenticalInputs() {
        CandidateFeatureScorer.CandidateFeatureScore a = score(candidate("A", "C", 120), 0.9d);
        CandidateFeatureScorer.CandidateFeatureScore b = score(candidate("B", "C", 119), 0.9d);

        OrderedSetResponse first = orderer.order(List.of(b, a), request(3), profile(), "snap");
        OrderedSetResponse second = orderer.order(List.of(b, a), request(3), profile(), "snap");

        assertThat(first.items()).extracting(item -> item.arrangementId())
                .containsExactlyElementsOf(second.items().stream().map(item -> item.arrangementId()).toList());
    }

    @Test
    void orderRespectsKeyCenterLimitWhenEnoughCandidatesExist() {
        CandidateFeatureScorer.CandidateFeatureScore cKey = score(candidate("C", "C", 120), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore gKey = score(candidate("G", "G", 122), 0.95d);
        CandidateFeatureScorer.CandidateFeatureScore dKey = score(candidate("D", "D", 121), 0.99d);

        OrderedSetResponse ordered = orderer.order(List.of(cKey, dKey, gKey), request(2), profile(), "snap");

        assertThat(ordered.items()).hasSize(2);
        assertThat(ordered.items()).extracting(item -> item.arrangementId())
                .containsExactly(cKey.candidate().arrangementId(), dKey.candidate().arrangementId());
    }

    @Test
    void tieBreakingUsesNormalizedTitleThenStableIdentifiers() {
        CandidateFeatureScorer.CandidateFeatureScore zeta = score(candidate("zeta", "C", 120), 0.9d);
        CandidateFeatureScorer.CandidateFeatureScore alpha = score(candidate("Alpha", "C", 120), 0.9d);

        OrderedSetResponse ordered = orderer.order(List.of(zeta, alpha), request(3), profile(), "snap");

        assertThat(ordered.items()).extracting(item -> item.arrangementId())
                .containsExactly(alpha.candidate().arrangementId(), zeta.candidate().arrangementId());
    }

    @Test
    void nearTieWithinEpsilonUsesTieBreakersInsteadOfRawTotalScore() {
        CandidateFeatureScorer.CandidateFeatureScore alphaLower = score(candidate("Alpha", "C", 120), 0.90000d);
        CandidateFeatureScorer.CandidateFeatureScore zetaHigher = score(candidate("zeta", "C", 120), 0.90005d);

        OrderedSetResponse ordered = orderer.order(List.of(zetaHigher, alphaLower), request(3), profile(), "snap");

        assertThat(ordered.items()).extracting(item -> item.arrangementId())
                .containsExactly(alphaLower.candidate().arrangementId(), zetaHigher.candidate().arrangementId());
    }

    @Test
    void emitsRoleFitAndEligibilityExplanationsForSelectedItems() {
        CandidateFeatureScorer.CandidateFeatureScore candidate = score(candidate("A", "C", 120), 0.9d);

        OrderedSetResponse ordered = orderer.order(List.of(candidate), request(2), profile(), "snap");

        assertThat(ordered.items()).hasSize(1);
        assertThat(ordered.items().get(0).explanationFacts())
                .extracting(fact -> fact.code())
                .contains("ROLE_FIT", "APPROVAL_ELIGIBLE");
        assertThat(ordered.items().get(0).songExplanation()).isNotNull();
        assertThat(ordered.items().get(0).songExplanation().songId()).isEqualTo(ordered.items().get(0).songId());
        assertThat(ordered.items().get(0).songExplanation().arrangementId()).isEqualTo(ordered.items().get(0).arrangementId());
        assertThat(ordered.items().get(0).songExplanation().facts())
                .extracting(fact -> fact.code())
                .contains("ROLE_FIT", "APPROVAL_ELIGIBLE");
    }


    @Test
    void emitsSetLevelFactsForSatisfiedScenario() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", "C", 120), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "G", 118), 0.9d);

        OrderedSetResponse ordered = orderer.order(List.of(first, second), request(2), profile(), "snap");

        assertThat(ordered.setExplanationFacts())
                .extracting(fact -> fact.code())
                .contains("COUNT_TARGET_MET", "KEY_CENTER_POLICY_MET");
        assertThat(ordered.setExplanationFacts())
                .filteredOn(fact -> "COUNT_TARGET_MET".equals(fact.code()))
                .allSatisfy(fact -> assertThat(fact.severity()).isEqualTo("info"));
    }

    @Test
    void emitsSetLevelWarningsWhenCatalogIsInsufficient() {
        CandidateFeatureScorer.CandidateFeatureScore onlyCandidate = score(candidate("A", "C", 120), 1.0d);

        OrderedSetResponse ordered = orderer.order(List.of(onlyCandidate), request(2), profile(), "snap");

        assertThat(ordered.setExplanationFacts())
                .extracting(fact -> fact.code())
                .contains("COUNT_TARGET_MET", "INSUFFICIENT_CANDIDATES");
        assertThat(ordered.setExplanationFacts())
                .filteredOn(fact -> "INSUFFICIENT_CANDIDATES".equals(fact.code()))
                .allSatisfy(fact -> {
                    assertThat(fact.severity()).isEqualTo("warning");
                    assertThat(fact.scope()).isEqualTo("set");
                });
    }


    @Test
    void setLevelExplanationsRepresentDefaultRequestPolicyAndCoverage() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidateWithMatchedTags(
                "A",
                "C",
                100,
                List.of(
                        new RecommendationTag(UUID.randomUUID(), TagType.THEME, "Holiness", "holiness"),
                        new RecommendationTag(UUID.randomUUID(), TagType.SCRIPTURE, "Psalm 24", "psalm-24"))), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidateWithMatchedTags(
                "B",
                "C",
                108,
                List.of(new RecommendationTag(UUID.randomUUID(), TagType.THEME, "Joy", "joy"))), 0.9d);
        ScoringRequest defaultRequest = new ScoringRequest(
                "Psalm 24",
                List.of("holiness"),
                10,
                5,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(true, true, true, false));

        OrderedSetResponse ordered = orderer.order(List.of(first, second), defaultRequest, profile(), "snap");

        RecommendationExplanationFact defaults = setFact(ordered, "REQUEST_DEFAULTS_APPLIED");
        assertThat(defaults.values())
                .containsEntry("defaultPraise", 10)
                .containsEntry("defaultWorship", 5)
                .containsEntry("preferSameKey", true)
                .containsEntry("allowRelativeMajorMinor", true)
                .containsEntry("maxKeyCenters", 2)
                .containsEntry("maxJumpBpm", 12);
        assertThat(setFact(ordered, "COUNT_TARGET_MET").values())
                .containsEntry("selected", 2)
                .containsEntry("target", 15)
                .containsEntry("selectedPraise", 2)
                .containsEntry("requestedPraise", 10)
                .containsEntry("requestedWorship", 5);
        assertThat(setFact(ordered, "KEY_CENTER_POLICY_MET").values())
                .containsEntry("preferSameKey", true)
                .containsEntry("allowRelativeMajorMinor", true)
                .containsEntry("keyCenters", "c");
        assertThat(setFact(ordered, "THEME_COVERAGE").values())
                .containsEntry("coveredThemes", "holiness")
                .containsEntry("requestedThemes", "holiness");
        assertThat(setFact(ordered, "SCRIPTURE_COVERAGE").values())
                .containsEntry("coveredScripture", "psalm-24");
    }

    @Test
    void setLevelExplanationsRepresentExplicitPolicyOverridesWithoutDefaultFact() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", "C", 100), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "C", KeyMode.MAJOR, 104, "4/4", 60, null), 0.9d);
        ScoringRequest explicitRequest = new ScoringRequest(
                null,
                List.of(),
                2,
                0,
                new ScoringRequest.KeyPolicy(false, false, 1),
                new ScoringRequest.TempoPolicy(5),
                "steady",
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(false, false, false, false));

        OrderedSetResponse ordered = orderer.order(List.of(first, second), explicitRequest, profile(), "snap");

        assertThat(ordered.setExplanationFacts())
                .extracting(RecommendationExplanationFact::code)
                .doesNotContain("REQUEST_DEFAULTS_APPLIED");
        assertThat(setFact(ordered, "KEY_CENTER_POLICY_MET").values())
                .containsEntry("preferSameKey", false)
                .containsEntry("allowRelativeMajorMinor", false)
                .containsEntry("maxKeyCenters", 1);
        assertThat(setFact(ordered, "TEMPO_POLICY_MET").values())
                .containsEntry("maxJumpBpm", 5)
                .containsEntry("maxObservedJumpBpm", 4);
        assertThat(setFact(ordered, "SET_ENERGY_ARC_MATCH").values())
                .containsEntry("requestedArc", "steady")
                .containsEntry("arcVersion", "energy-arc-v1");
    }

    @Test
    void setLevelWarningsDistinguishAcceptedTempoAndEnergyTradeoffs() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", "C", KeyMode.MAJOR, 100, "4/4", 90, null), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "C", KeyMode.MAJOR, 140, "4/4", 10, null), 0.9d);
        ScoringRequest tradeoffRequest = new ScoringRequest(
                null,
                List.of(),
                2,
                0,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                "rising",
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(false, false, false, false));

        OrderedSetResponse ordered = orderer.order(List.of(first, second), tradeoffRequest, profile(), "snap");

        assertThat(setFact(ordered, "COUNT_TARGET_MET").severity()).isEqualTo("info");
        assertThat(setFact(ordered, "TEMPO_POLICY_MET").severity()).isEqualTo("warning");
        assertThat(setFact(ordered, "TEMPO_POLICY_MET").values())
                .containsEntry("maxObservedJumpBpm", 40)
                .containsEntry("exceededJumps", 1);
        assertThat(setFact(ordered, "SET_ENERGY_ARC_MATCH").severity()).isEqualTo("warning");
        assertThat((String) setFact(ordered, "SET_ENERGY_ARC_MATCH").values().get("tradeoffs"))
                .contains("ENERGY_ARC_SHAPE_MISMATCH");
    }

    @Test
    void setLevelWarningsDistinguishInsufficientEligibleCatalog() {
        CandidateFeatureScorer.CandidateFeatureScore onlyCandidate = score(candidate("A", "C", 120), 1.0d);

        OrderedSetResponse ordered = orderer.order(List.of(onlyCandidate), request(2), profile(), "snap");

        RecommendationExplanationFact insufficient = setFact(ordered, "INSUFFICIENT_CANDIDATES");
        assertThat(insufficient.severity()).isEqualTo("warning");
        assertThat(insufficient.values())
                .containsEntry("selected", 1)
                .containsEntry("target", 2)
                .containsEntry("availableCandidates", 1)
                .containsEntry("limitation", "insufficient_eligible_catalog");
    }

    @Test
    void emitsDirectionalTransitionEntryForAdjacentItems() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", "C", 120), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "G", 150), 0.9d);

        OrderedSetResponse ordered = orderer.order(List.of(first, second), request(3), profile(), "snap");

        assertThat(ordered.items()).hasSize(2);
        assertThat(ordered.adjacentTransitionExplanations()).hasSize(1);
        assertThat(ordered.adjacentTransitionExplanations().get(0).source().arrangementId())
                .isEqualTo(ordered.items().get(0).arrangementId());
        assertThat(ordered.adjacentTransitionExplanations().get(0).target().arrangementId())
                .isEqualTo(ordered.items().get(1).arrangementId());
        assertThat(ordered.adjacentTransitionExplanations().get(0).reasonCodes())
                .contains("MODULATION_PENALTY", "TEMPO_POLICY_OK", "TEMPO_TRADEOFF_ACCEPTED");
        assertThat(ordered.adjacentTransitionExplanations().get(0).tempoChange().jumpBpm()).isEqualTo(30);
        assertThat(ordered.items().get(1).explanationFacts())
                .filteredOn(fact -> "transition".equals(fact.scope()))
                .allSatisfy(fact -> {
                    assertThat(fact.subject().sourceId()).isEqualTo(ordered.items().get(0).arrangementId().toString());
                    assertThat(fact.subject().targetId()).isEqualTo(ordered.items().get(1).arrangementId().toString());
                });
    }

    @Test
    void emitsAllowedRelativeKeyAndParserCompatibilityFacts() {
        ArrangementTransitionMetadata parserMetadata = new ArrangementTransitionMetadata(true, 0.92d, "parser-run-1:cadence");
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", "C", KeyMode.MAJOR, 100, "4/4", 60, parserMetadata), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "A", KeyMode.MINOR, 108, "4/4", 65, parserMetadata), 0.9d);

        OrderedSetResponse ordered = orderer.order(List.of(first, second), request(3), profile(), "snap");

        assertThat(ordered.adjacentTransitionExplanations()).hasSize(1);
        assertThat(ordered.adjacentTransitionExplanations().get(0).reasonCodes())
                .contains("RELATIVE_KEY_TRANSITION", "ARRANGEMENT_COMPATIBLE")
                .doesNotContain("MODULATION_PENALTY");
        assertThat(ordered.adjacentTransitionExplanations().get(0).keyChange().relativeMajorMinor()).isTrue();
        assertThat(ordered.adjacentTransitionExplanations().get(0).arrangementCompatibility().parserConfidence())
                .isEqualTo(0.92d);
    }

    @Test
    void exposesPenalizedMeterAndEnergyMovementWithoutHidingPenalties() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", "C", KeyMode.MAJOR, 100, "4/4", 20, null), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "F#", KeyMode.MAJOR, 108, "6/8", 95, null), 0.9d);

        OrderedSetResponse ordered = orderer.order(List.of(first, second), request(3), profile(), "snap");

        assertThat(ordered.adjacentTransitionExplanations().get(0).reasonCodes())
                .contains("MODULATION_PENALTY", "METER_COMPATIBLE", "ENERGY_ARC_MATCH");
        assertThat(ordered.adjacentTransitionExplanations().get(0).meterChange().compatible()).isFalse();
        assertThat(ordered.adjacentTransitionExplanations().get(0).energyMovement().delta()).isEqualTo(75);
        assertThat(ordered.adjacentTransitionExplanations().get(0).warnings())
                .extracting(warning -> warning.code())
                .contains("MODULATION_PENALTY");
    }

    @Test
    void warnsAboutMissingMetadataAndDoesNotInventUnavailableFacts() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", null, KeyMode.MAJOR, 0, null, 0, null), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "C", KeyMode.MAJOR, 108, "4/4", 65, null), 0.9d);

        OrderedSetResponse ordered = orderer.order(List.of(first, second), request(3), profile(), "snap");

        assertThat(ordered.adjacentTransitionExplanations()).hasSize(1);
        assertThat(ordered.adjacentTransitionExplanations().get(0).keyChange()).isNull();
        assertThat(ordered.adjacentTransitionExplanations().get(0).tempoChange()).isNull();
        assertThat(ordered.adjacentTransitionExplanations().get(0).meterChange()).isNull();
        assertThat(ordered.adjacentTransitionExplanations().get(0).warnings())
                .extracting(warning -> warning.code())
                .contains("TRANSITION_METADATA_MISSING");
        assertThat(ordered.adjacentTransitionExplanations().get(0).reasonCodes())
                .doesNotContain("SAME_KEY_TRANSITION", "TEMPO_POLICY_OK", "METER_COMPATIBLE");
    }

    @Test
    void transitionEntriesFollowDeterministicAdjacentOrdering() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", "C", 100), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "C", 104), 0.9d);
        CandidateFeatureScorer.CandidateFeatureScore third = score(candidate("D", "C", 108), 0.8d);

        OrderedSetResponse ordered = orderer.order(List.of(third, first, second), request(3, false, 3), profile(), "snap");

        assertThat(ordered.adjacentTransitionExplanations()).hasSize(2);
        assertThat(ordered.adjacentTransitionExplanations())
                .extracting(entry -> entry.id())
                .containsExactly("transition-1-2", "transition-2-3");
        assertThat(ordered.adjacentTransitionExplanations())
                .extracting(entry -> entry.source().position() + "->" + entry.target().position())
                .containsExactly("1->2", "2->3");
    }

    @Test
    void omitsAdminExclusionFactsByDefault() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", "C", 120), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "G", 118), 0.9d);
        CandidateFeatureScorer.CandidateFeatureScore third = score(candidate("D", "D", 116), 0.8d);

        OrderedSetResponse ordered = orderer.order(List.of(first, second, third), request(2), profile(), "snap");

        assertThat(ordered.adminCandidateExplanationFacts()).isEmpty();
        assertThat(ordered.items()).extracting(item -> item.arrangementId())
                .containsExactly(first.candidate().arrangementId(), second.candidate().arrangementId());
    }

    @Test
    void emitsAdminExclusionFactsWithoutChangingSelection() {
        CandidateFeatureScorer.CandidateFeatureScore first = score(candidate("A", "C", 120), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore second = score(candidate("B", "G", 118), 0.9d);
        CandidateFeatureScorer.CandidateFeatureScore third = score(candidate("D", "D", 116), 0.8d);

        OrderedSetResponse baseline = orderer.order(List.of(first, second, third), request(2), profile(), "snap");
        OrderedSetResponse admin = orderer.order(List.of(first, second, third), request(2, true), profile(), "snap");

        assertThat(admin.items()).extracting(item -> item.arrangementId())
                .containsExactlyElementsOf(baseline.items().stream().map(item -> item.arrangementId()).toList());
        assertThat(admin.adminCandidateExplanationFacts())
                .extracting(fact -> fact.code())
                .contains("EXCLUDED_QUOTA_FILLED");

        OrderedSetResponse publicView = admin.asPublicResponse();
        assertThat(publicView.adminCandidateExplanationFacts()).isEmpty();
        assertThat(publicView.items()).extracting(item -> item.arrangementId())
                .containsExactlyElementsOf(admin.items().stream().map(item -> item.arrangementId()).toList());
        assertThat(publicView.items()).extracting(item -> item.candidateTotalScore())
                .containsExactlyElementsOf(admin.items().stream().map(item -> item.candidateTotalScore()).toList());
        assertThat(publicView.totalScore()).isEqualTo(admin.totalScore());
        assertThat(publicView.setExplanationFacts()).containsExactlyElementsOf(admin.setExplanationFacts());
    }

    private static RecommendationExplanationFact setFact(OrderedSetResponse response, String code) {
        return response.setExplanationFacts().stream()
                .filter(fact -> code.equals(fact.code()))
                .findFirst()
                .orElseThrow();
    }

    private static CandidateFeatureScorer.CandidateFeatureScore score(RecommendableArrangement candidate, double total) {
        return new CandidateFeatureScorer.CandidateFeatureScore(
                candidate,
                List.of(
                        new ScoringComponentScore("theme_match", total, total),
                        new ScoringComponentScore("role_fit", 1.0d, 0.2d),
                        new ScoringComponentScore("metadata_confidence", 1.0d, 0.1d)),
                total);
    }

    private static ScoringRequest request(int maxKeyCenters) {
        return request(maxKeyCenters, false);
    }

    private static ScoringRequest request(int maxKeyCenters, boolean includeAdminExplanations) {
        return request(maxKeyCenters, includeAdminExplanations, 2);
    }

    private static ScoringRequest request(int maxKeyCenters, boolean includeAdminExplanations, int praiseCount) {
        return new ScoringRequest(
                null,
                List.of(),
                praiseCount,
                0,
                new ScoringRequest.KeyPolicy(true, true, maxKeyCenters),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                includeAdminExplanations,
                new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile profile() {
        return new ScoringProfile(
                "v1",
                Map.of(TransitionScorer.BPM_JUMP, 0.2d, TransitionScorer.KEY_SAME, 0.3d),
                List.of("total_score(desc,epsilon=0.0001)", "approval_confidence(desc)", "normalized_title(asc)", "song_id(asc)", "arrangement_id(asc)"));
    }

    private static RecommendableArrangement candidate(String titleSuffix, String key, int bpm) {
        return candidate(titleSuffix, key, KeyMode.MAJOR, bpm, "4/4", 70, null);
    }

    private static RecommendableArrangement candidateWithMatchedTags(
            String titleSuffix,
            String key,
            int bpm,
            List<RecommendationTag> matchedTags) {
        RecommendableArrangement base = candidate(titleSuffix, key, bpm);
        return new RecommendableArrangement(
                base.arrangementId(),
                base.songId(),
                base.currentLyricsDocumentId(),
                base.title(),
                base.language(),
                base.musicalKey(),
                base.keyMode(),
                base.bpm(),
                base.timeSignature(),
                base.energy(),
                base.tags(),
                List.of(),
                matchedTags,
                base.approvalGateSummary(),
                base.transitionMetadata());
    }

    private static UUID arrangementId(String titleSuffix) {
        return deterministicId("00000000-0000-0000-0000-0000000000", titleSuffix);
    }

    private static UUID songId(String titleSuffix) {
        return deterministicId("00000000-0000-0000-0000-0000000001", titleSuffix);
    }

    private static UUID lyricsDocumentId(String titleSuffix) {
        return deterministicId("00000000-0000-0000-0000-0000000002", titleSuffix);
    }

    private static UUID deterministicId(String prefix, String titleSuffix) {
        int offset = switch (titleSuffix) {
            case "A" -> 1;
            case "B" -> 2;
            case "C" -> 3;
            case "D" -> 4;
            default -> 9;
        };
        return UUID.fromString(prefix + String.format("%02d", offset));
    }

    private static RecommendableArrangement candidate(
            String titleSuffix,
            String key,
            KeyMode keyMode,
            int bpm,
            String meter,
            int energy,
            ArrangementTransitionMetadata transitionMetadata) {
        return new RecommendableArrangement(
                arrangementId(titleSuffix),
                songId(titleSuffix),
                lyricsDocumentId(titleSuffix),
                "Song " + titleSuffix,
                "en",
                key,
                keyMode,
                bpm,
                meter,
                energy,
                List.of("praise"),
                List.of(),
                List.of(),
                new ApprovalGateSummary(
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED),
                transitionMetadata);
    }
}

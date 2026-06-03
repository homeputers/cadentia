package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.TagType;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemExplanationFactoryTest {

    private final CandidateFeatureScorer scorer = new CandidateFeatureScorer();
    private final ItemExplanationFactory factory = new ItemExplanationFactory();

    @Test
    void assemblesPerSongExplanationFromSelectedArrangementAndScoringEvidence() {
        // Arrange
        UUID arrangementId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID songId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        RecommendationTag theme = new RecommendationTag(
                UUID.fromString("00000000-0000-0000-0000-000000000301"), TagType.THEME, "Holiness", "holiness");
        RecommendationTag scripture = new RecommendationTag(
                UUID.fromString("00000000-0000-0000-0000-000000000302"), TagType.SCRIPTURE, "Psalm 24", "psalm-24");
        RecommendableArrangement candidate = candidate(arrangementId, songId, "G", 118, "4/4", List.of(theme, scripture));
        CandidateFeatureScorer.CandidateFeatureScore score = scorer.scoreCandidate(candidate, request(), CandidateFeatureScorerTestProfile.profile());

        // Act
        RecommendationSongExplanation explanation = factory.buildSongExplanation(candidate, request(), score.componentScores(), 3);

        // Assert
        assertThat(explanation.songId()).isEqualTo(songId);
        assertThat(explanation.arrangementId()).isEqualTo(arrangementId);
        assertThat(explanation.position()).isEqualTo(3);
        assertThat(explanation.facts()).extracting(RecommendationExplanationFact::code)
                .contains("ROLE_FIT", "APPROVAL_ELIGIBLE", "THEME_MATCH", "SCRIPTURE_MATCH");
        assertThat(explanation.scoreComponents())
                .filteredOn(component -> CandidateFeatureScorer.THEME_MATCH.equals(component.componentCode()))
                .singleElement()
                .satisfies(component -> {
                    assertThat(component.weight()).isEqualTo(0.3d);
                    assertThat(component.reasonCodes()).contains("CONTRIBUTION_POSITIVE");
                    assertThat(component.weightedContribution()).isGreaterThan(0.0d);
                });
        assertThat(explanation.themeEvidence())
                .singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.type()).isEqualTo("catalog");
                    assertThat(evidence.ref()).isEqualTo("tag:" + theme.id());
                    assertThat(evidence.field()).isEqualTo("theme:holiness");
                });
        assertThat(explanation.scriptureEvidence())
                .singleElement()
                .extracting(RecommendationExplanationEvidence::ref)
                .isEqualTo("tag:" + scripture.id());
        assertThat(explanation.approvalEvidence()).hasSize(8);
        assertThat(explanation.provenanceEvidence())
                .singleElement()
                .extracting(RecommendationExplanationEvidence::field)
                .isEqualTo("current_lyrics_document_id");
        assertThat(explanation.uiDisplayHints()).extracting(RecommendationSongExplanation.UiDisplayHint::templateKey)
                .contains("item.theme_match", "item.scripture_match");
    }

    @Test
    void omitsUnusedThemeAndScriptureTagsInsteadOfClaimingUnsupportedMatches() {
        // Arrange
        RecommendationTag unusedTheme = new RecommendationTag(UUID.randomUUID(), TagType.THEME, "Joy", "joy");
        RecommendationTag unusedScripture = new RecommendationTag(UUID.randomUUID(), TagType.SCRIPTURE, "John 3:16", "john-3-16");
        RecommendableArrangement candidate = candidate(UUID.randomUUID(), UUID.randomUUID(), "G", 118, "4/4", List.of(unusedTheme, unusedScripture));
        CandidateFeatureScorer.CandidateFeatureScore score = scorer.scoreCandidate(candidate, request(), CandidateFeatureScorerTestProfile.profile());

        // Act
        RecommendationSongExplanation explanation = factory.buildSongExplanation(candidate, request(), score.componentScores(), 1);

        // Assert
        assertThat(explanation.facts()).extracting(RecommendationExplanationFact::code)
                .doesNotContain("THEME_MATCH", "SCRIPTURE_MATCH");
        assertThat(explanation.themeEvidence()).isEmpty();
        assertThat(explanation.scriptureEvidence()).isEmpty();
    }

    @Test
    void convertsMissingOrLowConfidenceMetadataIntoWarningsWithoutInventingMetadataEvidence() {
        // Arrange
        RecommendableArrangement candidate = candidate(UUID.randomUUID(), UUID.randomUUID(), "", 0, "", List.of());
        CandidateFeatureScorer.CandidateFeatureScore score = scorer.scoreCandidate(candidate, request(), CandidateFeatureScorerTestProfile.profile());

        // Act
        RecommendationSongExplanation explanation = factory.buildSongExplanation(candidate, request(), score.componentScores(), 1);

        // Assert
        assertThat(explanation.catalogMetadataReferences()).isEmpty();
        assertThat(explanation.warnings()).extracting(RecommendationExplanationFact::code)
                .contains("METADATA_LOW_CONFIDENCE");
        assertThat(explanation.warnings())
                .filteredOn(fact -> "METADATA_LOW_CONFIDENCE".equals(fact.code()))
                .singleElement()
                .satisfies(fact -> assertThat(fact.evidence())
                        .extracting(RecommendationExplanationEvidence::field)
                        .contains("missing_musical_key", "missing_bpm", "missing_time_signature"));
    }

    private static RecommendableArrangement candidate(
            UUID arrangementId,
            UUID songId,
            String key,
            int bpm,
            String meter,
            List<RecommendationTag> matchedTags) {
        return new RecommendableArrangement(
                arrangementId,
                songId,
                UUID.randomUUID(),
                "Song",
                "en",
                key,
                KeyMode.MAJOR,
                bpm,
                meter,
                70,
                List.of("praise"),
                matchedTags,
                matchedTags,
                approvedSummary());
    }

    private static ScoringRequest request() {
        return new ScoringRequest(
                "Psalm 24",
                List.of("holiness"),
                1,
                0,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(false, false, false, false));
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

    private static final class CandidateFeatureScorerTestProfile {

        private static ScoringProfile profile() {
            return new ScoringProfile(
                    "v1",
                    java.util.Map.of(
                            CandidateFeatureScorer.THEME_MATCH, 0.3,
                            CandidateFeatureScorer.SCRIPTURE_MATCH, 0.2,
                            CandidateFeatureScorer.ROLE_FIT, 0.2,
                            CandidateFeatureScorer.MUSICAL_FIT, 0.1,
                            CandidateFeatureScorer.ENERGY_FIT, 0.1,
                            CandidateFeatureScorer.METADATA_CONFIDENCE, 0.1,
                            CandidateFeatureScorer.FEEDBACK_TUNING, 0.1),
                    List.of("total_score", "song_id", "arrangement_id"));
        }
    }
}

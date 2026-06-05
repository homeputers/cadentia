package com.cadentia.reng.scoring;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.TagType;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ItemExplanationFactory {

    private final TeamSuitabilityEvaluator teamSuitabilityEvaluator = new TeamSuitabilityEvaluator();

    public List<RecommendationExplanationFact> build(
            RecommendableArrangement candidate,
            ScoringRequest request,
            List<ScoringComponentScore> componentScores) {
        return buildFacts(candidate, request, componentScores);
    }

    public RecommendationSongExplanation buildSongExplanation(
            RecommendableArrangement candidate,
            ScoringRequest request,
            List<ScoringComponentScore> componentScores,
            int position) {
        List<RecommendationExplanationFact> facts = buildFacts(candidate, request, componentScores);
        return new RecommendationSongExplanation(
                candidate.songId(),
                candidate.arrangementId(),
                position,
                facts,
                componentScores.stream().map(ScoreComponentExplanation::from).toList(),
                catalogMetadataReferences(candidate),
                evidenceFor(facts, "THEME_MATCH"),
                evidenceFor(facts, "SCRIPTURE_MATCH"),
                approvalEvidence(candidate.approvalGateSummary()),
                provenanceEvidence(candidate),
                facts.stream().filter(fact -> "warning".equals(fact.severity())).toList(),
                facts.stream()
                        .map(fact -> new RecommendationSongExplanation.UiDisplayHint(
                                displayGroup(fact), fact.severity(), fact.templateKey(), List.of(fact.code())))
                        .toList());
    }

    private List<RecommendationExplanationFact> buildFacts(
            RecommendableArrangement candidate,
            ScoringRequest request,
            List<ScoringComponentScore> componentScores) {
        List<RecommendationExplanationFact> facts = new ArrayList<>();
        RecommendationExplanationSubject subject =
                new RecommendationExplanationSubject("arrangement", candidate.arrangementId().toString());

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.ROLE_FIT.equals(score.componentCode()))
                .findFirst()
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "ROLE_FIT",
                        "info",
                        "item",
                        subject,
                        "item.role_fit",
                        Map.of("score", score.rawScore()),
                        List.of(new RecommendationExplanationEvidence("score", "candidate.role_fit", "raw", null)),
                        score.weightedContribution())));

        facts.add(new RecommendationExplanationFact(
                "APPROVAL_ELIGIBLE",
                "info",
                "item",
                subject,
                "item.approval_eligible",
                Map.of("hasProvenance", candidate.currentLyricsDocumentId() != null),
                approvalAndProvenanceEvidence(candidate),
                null));

        List<RecommendationTag> matchedThemes = matchedRequestedThemeTags(candidate, request, componentScores);
        if (!matchedThemes.isEmpty()) {
            facts.add(new RecommendationExplanationFact(
                    "THEME_MATCH",
                    "info",
                    "item",
                    subject,
                    "item.theme_match",
                    Map.of("themes", matchedThemes.stream().map(RecommendationTag::slug).collect(Collectors.joining(","))),
                    matchedThemes.stream()
                            .map(tag -> new RecommendationExplanationEvidence(
                                    "catalog", "tag:" + tag.id(), "theme:" + tag.slug(), 1.0d))
                            .toList(),
                    findImpact(componentScores, CandidateFeatureScorer.THEME_MATCH)));
        }

        List<RecommendationTag> matchedScriptures = matchedRequestedScriptureTags(candidate, request, componentScores);
        if (!matchedScriptures.isEmpty()) {
            facts.add(new RecommendationExplanationFact(
                    "SCRIPTURE_MATCH",
                    "info",
                    "item",
                    subject,
                    "item.scripture_match",
                    Map.of("scripture", matchedScriptures.stream().map(RecommendationTag::name).collect(Collectors.joining(","))),
                    matchedScriptures.stream()
                            .map(tag -> new RecommendationExplanationEvidence(
                                    "catalog", "tag:" + tag.id(), "scripture:" + tag.slug(), 1.0d))
                            .toList(),
                    findImpact(componentScores, CandidateFeatureScorer.SCRIPTURE_MATCH)));
        }

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.MUSICAL_FIT.equals(score.componentCode()))
                .findFirst()
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "SCORE_COMPONENT_MUSICAL_FIT",
                        "info",
                        "item",
                        subject,
                        "item.score_component_musical_fit",
                        Map.of("score", score.rawScore()),
                        catalogMetadataReferences(candidate),
                        score.weightedContribution())));

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.ENERGY_FIT.equals(score.componentCode()))
                .findFirst()
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "SCORE_COMPONENT_ENERGY_FIT",
                        "info",
                        "item",
                        subject,
                        "item.score_component_energy_fit",
                        Map.of("score", score.rawScore()),
                        energyEvidence(candidate),
                        score.weightedContribution())));

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.METADATA_CONFIDENCE.equals(score.componentCode()))
                .findFirst()
                .filter(score -> score.rawScore() < 1.0d)
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "METADATA_LOW_CONFIDENCE",
                        "warning",
                        "item",
                        subject,
                        "item.metadata_low_confidence",
                        Map.of("confidence", score.rawScore()),
                        missingMetadataEvidence(candidate, score.rawScore()),
                        score.weightedContribution())));

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.FEEDBACK_TUNING.equals(score.componentCode()))
                .findFirst()
                .filter(score -> score.rawScore() != 0.0d)
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "FEEDBACK_TUNING",
                        score.rawScore() > 0.0d ? "info" : "warning",
                        "item",
                        subject,
                        "item.feedback_tuning",
                        Map.of("feedbackContribution", score.rawScore()),
                        List.of(new RecommendationExplanationEvidence("score", "feedback.aggregate", "raw", score.rawScore())),
                        score.weightedContribution())));

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.TEAM_SUITABILITY.equals(score.componentCode()))
                .findFirst()
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "SCORE_COMPONENT_TEAM_SUITABILITY",
                        "info",
                        "item",
                        subject,
                        "item.score_component_team_suitability",
                        Map.of("score", score.rawScore()),
                        List.of(new RecommendationExplanationEvidence("score", "candidate.team_suitability", "raw", score.rawScore())),
                        score.weightedContribution())));

        teamSuitabilityEvaluator.evaluate(candidate, request).facts().stream()
                .map(fact -> teamSuitabilityExplanation(subject, fact))
                .forEach(facts::add);

        return List.copyOf(facts);
    }

    private static RecommendationExplanationFact teamSuitabilityExplanation(
            RecommendationExplanationSubject subject,
            TeamSuitabilityModels.TeamSuitabilityFact fact) {
        return new RecommendationExplanationFact(
                teamExplanationCode(fact),
                teamSeverity(fact.status()),
                "item",
                subject,
                teamTemplateKey(fact),
                fact.values(),
                fact.evidence(),
                null);
    }

    private static String teamExplanationCode(TeamSuitabilityModels.TeamSuitabilityFact fact) {
        return switch (fact.code()) {
            case MISSING_REQUIRED_INSTRUMENT -> "TEAM_REQUIRED_INSTRUMENT_COVERAGE";
            case OPTIONAL_INSTRUMENT_FIT -> "TEAM_OPTIONAL_INSTRUMENT_FIT";
            case MISSING_VOCAL_CONFIGURATION -> "TEAM_VOCAL_CONFIGURATION";
            case LEAD_VOCAL_RANGE_MISMATCH -> "TEAM_LEAD_VOCAL_RANGE_FIT";
            case INSUFFICIENT_SKILL_COVERAGE -> "TEAM_SKILL_LEVEL_FLOOR";
            case UNAVAILABLE_ASSIGNED_MUSICIAN -> "TEAM_AVAILABILITY_STATUS";
            case INCOMPLETE_TEAM -> "TEAM_READINESS_WARNING";
            case ASSIGNMENT_STATUS -> "TEAM_ASSIGNMENT_STATUS";
        };
    }

    private static String teamTemplateKey(TeamSuitabilityModels.TeamSuitabilityFact fact) {
        return switch (teamExplanationCode(fact)) {
            case "TEAM_REQUIRED_INSTRUMENT_COVERAGE" -> "team.required_instrument_coverage";
            case "TEAM_OPTIONAL_INSTRUMENT_FIT" -> "team.optional_instrument_fit";
            case "TEAM_VOCAL_CONFIGURATION" -> "team.vocal_configuration";
            case "TEAM_LEAD_VOCAL_RANGE_FIT" -> "team.lead_vocal_range_fit";
            case "TEAM_SKILL_LEVEL_FLOOR" -> "team.skill_level_floor";
            case "TEAM_AVAILABILITY_STATUS" -> "team.availability_status";
            case "TEAM_ASSIGNMENT_STATUS" -> "team.assignment_status";
            case "TEAM_READINESS_WARNING" -> "team.readiness_warning";
            default -> throw new IllegalArgumentException("Unsupported team fact: " + fact.code());
        };
    }

    private static String teamSeverity(TeamSuitabilityModels.FactStatus status) {
        return switch (status) {
            case PASS -> "info";
            case WARNING -> "warning";
            case FAIL -> "blocked";
        };
    }

    private static List<RecommendationTag> matchedRequestedThemeTags(
            RecommendableArrangement candidate,
            ScoringRequest request,
            List<ScoringComponentScore> componentScores) {
        if (request.themeHints().isEmpty() || rawScore(componentScores, CandidateFeatureScorer.THEME_MATCH) <= 0.0d) {
            return List.of();
        }
        Set<String> requestedThemes = request.themeHints().stream()
                .map(ItemExplanationFactory::normalize)
                .collect(Collectors.toUnmodifiableSet());
        return candidate.matchedTags().stream()
                .filter(tag -> tag.tagType() == TagType.THEME)
                .filter(tag -> requestedThemes.contains(normalize(tag.slug())) || requestedThemes.contains(normalize(tag.name())))
                .toList();
    }

    private static List<RecommendationTag> matchedRequestedScriptureTags(
            RecommendableArrangement candidate,
            ScoringRequest request,
            List<ScoringComponentScore> componentScores) {
        if (request.verseText() == null
                || request.verseText().isBlank()
                || rawScore(componentScores, CandidateFeatureScorer.SCRIPTURE_MATCH) <= 0.0d) {
            return List.of();
        }
        String verse = normalize(request.verseText());
        return candidate.matchedTags().stream()
                .filter(tag -> tag.tagType() == TagType.SCRIPTURE)
                .filter(tag -> {
                    String tagValue = normalize(tag.name() + " " + tag.slug());
                    return tagValue.contains(verse) || verse.contains(tagValue);
                })
                .toList();
    }

    private static List<RecommendationExplanationEvidence> approvalAndProvenanceEvidence(RecommendableArrangement candidate) {
        List<RecommendationExplanationEvidence> evidence = new ArrayList<>(approvalEvidence(candidate.approvalGateSummary()));
        evidence.addAll(provenanceEvidence(candidate));
        return List.copyOf(evidence);
    }

    private static List<RecommendationExplanationEvidence> approvalEvidence(ApprovalGateSummary summary) {
        if (summary == null) {
            return List.of();
        }
        List<RecommendationExplanationEvidence> evidence = new ArrayList<>();
        addApprovalEvidence(evidence, "song_doctrinal_status", summary.songDoctrinalStatus());
        addApprovalEvidence(evidence, "song_editorial_status", summary.songEditorialStatus());
        addApprovalEvidence(evidence, "song_licensing_status", summary.songLicensingStatus());
        addApprovalEvidence(evidence, "arrangement_musical_status", summary.arrangementMusicalStatus());
        addApprovalEvidence(evidence, "arrangement_editorial_status", summary.arrangementEditorialStatus());
        addApprovalEvidence(evidence, "lyrics_doctrinal_status", summary.lyricsDoctrinalStatus());
        addApprovalEvidence(evidence, "lyrics_editorial_status", summary.lyricsEditorialStatus());
        addApprovalEvidence(evidence, "lyrics_licensing_status", summary.lyricsLicensingStatus());
        return List.copyOf(evidence);
    }

    private static void addApprovalEvidence(
            List<RecommendationExplanationEvidence> evidence,
            String field,
            ApprovalStatus status) {
        if (status == ApprovalStatus.APPROVED) {
            evidence.add(new RecommendationExplanationEvidence("approval", "approval_gate_summary", field, 1.0d));
        }
    }

    private static List<RecommendationExplanationEvidence> provenanceEvidence(RecommendableArrangement candidate) {
        if (candidate.currentLyricsDocumentId() == null) {
            return List.of();
        }
        return List.of(new RecommendationExplanationEvidence(
                "provenance", "lyrics_document:" + candidate.currentLyricsDocumentId(), "current_lyrics_document_id", 1.0d));
    }

    private static List<RecommendationExplanationEvidence> catalogMetadataReferences(RecommendableArrangement candidate) {
        List<RecommendationExplanationEvidence> evidence = new ArrayList<>();
        if (candidate.musicalKey() != null && !candidate.musicalKey().isBlank()) {
            evidence.add(new RecommendationExplanationEvidence("catalog", "arrangement:" + candidate.arrangementId(), "musical_key", 1.0d));
        }
        if (candidate.bpm() > 0) {
            evidence.add(new RecommendationExplanationEvidence("catalog", "arrangement:" + candidate.arrangementId(), "bpm", 1.0d));
        }
        if (candidate.timeSignature() != null && !candidate.timeSignature().isBlank()) {
            evidence.add(new RecommendationExplanationEvidence("catalog", "arrangement:" + candidate.arrangementId(), "time_signature", 1.0d));
        }
        return List.copyOf(evidence);
    }

    private static List<RecommendationExplanationEvidence> energyEvidence(RecommendableArrangement candidate) {
        if (candidate.energy() <= 0) {
            return List.of();
        }
        return List.of(new RecommendationExplanationEvidence(
                "catalog", "arrangement:" + candidate.arrangementId(), "energy", 1.0d));
    }

    private static List<RecommendationExplanationEvidence> missingMetadataEvidence(
            RecommendableArrangement candidate,
            double confidence) {
        List<String> missing = new ArrayList<>();
        if (candidate.musicalKey() == null || candidate.musicalKey().isBlank()) {
            missing.add("musical_key");
        }
        if (candidate.bpm() <= 0) {
            missing.add("bpm");
        }
        if (candidate.timeSignature() == null || candidate.timeSignature().isBlank()) {
            missing.add("time_signature");
        }
        if (missing.isEmpty()) {
            return List.of(new RecommendationExplanationEvidence(
                    "catalog", "arrangement:" + candidate.arrangementId(), "metadata", confidence));
        }
        return missing.stream()
                .map(field -> new RecommendationExplanationEvidence(
                        "catalog", "arrangement:" + candidate.arrangementId(), "missing_" + field, confidence))
                .toList();
    }

    private static List<RecommendationExplanationEvidence> evidenceFor(
            List<RecommendationExplanationFact> facts,
            String code) {
        return facts.stream()
                .filter(fact -> code.equals(fact.code()))
                .flatMap(fact -> fact.evidence().stream())
                .toList();
    }

    private static Double findImpact(List<ScoringComponentScore> componentScores, String componentCode) {
        return componentScores.stream()
                .filter(score -> componentCode.equals(score.componentCode()))
                .map(ScoringComponentScore::weightedContribution)
                .findFirst()
                .orElse(null);
    }

    private static double rawScore(List<ScoringComponentScore> componentScores, String componentCode) {
        return componentScores.stream()
                .filter(score -> componentCode.equals(score.componentCode()))
                .mapToDouble(ScoringComponentScore::rawScore)
                .findFirst()
                .orElse(0.0d);
    }

    private static String displayGroup(RecommendationExplanationFact fact) {
        if (fact.templateKey().startsWith("item.metadata")) {
            return "warnings";
        }
        if (fact.templateKey().contains("theme") || fact.templateKey().contains("scripture")) {
            return "theme_scripture";
        }
        if (fact.templateKey().contains("score_component") || fact.templateKey().contains("feedback")) {
            return "score_components";
        }
        if (fact.templateKey().startsWith("team.")) {
            return "team_suitability";
        }
        if (fact.templateKey().contains("approval")) {
            return "eligibility";
        }
        return "item_fit";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

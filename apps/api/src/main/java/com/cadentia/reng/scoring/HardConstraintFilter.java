package com.cadentia.reng.scoring;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ConstraintContributionType;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ValidatedPluginContributions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class HardConstraintFilter {

    private final TeamSuitabilityEvaluator teamSuitabilityEvaluator = new TeamSuitabilityEvaluator();

    public HardFilterResult filter(List<RecommendableArrangement> candidates, ScoringRequest request) {
        return filter(candidates, request, null);
    }

    public HardFilterResult filter(List<RecommendableArrangement> candidates, ScoringRequest request, ScoringProfile profile) {
        return filter(candidates, request, profile, null);
    }

    public HardFilterResult filter(
            List<RecommendableArrangement> candidates,
            ScoringRequest request,
            ScoringProfile profile,
            ValidatedPluginContributions pluginContributions) {
        List<RecommendableArrangement> eligible = new ArrayList<>();
        List<HardFilterResult.ExcludedCandidate> excluded = new ArrayList<>();
        Set<UUID> excludedSongIds = request.excludedSongIds().stream()
                .map(UUID::fromString)
                .collect(java.util.stream.Collectors.toSet());

        for (RecommendableArrangement candidate : candidates) {
            List<HardFilterReasonCode> reasons = new ArrayList<>(exclusionReasons(candidate, request.language(), excludedSongIds));
            if (reasons.isEmpty()) {
                reasons.addAll(teamExclusionReasons(candidate, request, profile));
            }
            if (reasons.isEmpty() && hardRejectedByPlugin(candidate, pluginContributions)) {
                reasons.add(HardFilterReasonCode.PLUGIN_HARD_REJECT);
            }
            if (reasons.isEmpty()) {
                eligible.add(candidate);
            } else {
                excluded.add(new HardFilterResult.ExcludedCandidate(candidate, reasons));
            }
        }

        return new HardFilterResult(
                eligible,
                excluded,
                new HardFilterResult.CountRequirement(request.praiseCount(), request.worshipCount()));
    }

    private static boolean hardRejectedByPlugin(
            RecommendableArrangement candidate,
            ValidatedPluginContributions pluginContributions) {
        return pluginContributions != null
                && pluginContributions.constraintsByArrangement()
                        .getOrDefault(candidate.arrangementId(), List.of())
                        .stream()
                        .anyMatch(contribution -> contribution.type() == ConstraintContributionType.HARD_REJECT);
    }

    private List<HardFilterReasonCode> teamExclusionReasons(
            RecommendableArrangement candidate, ScoringRequest request, ScoringProfile profile) {
        if (profile == null || request.explicitTeamConstraints() == null) {
            return List.of();
        }
        return teamSuitabilityEvaluator.evaluate(candidate, request).facts().stream()
                .filter(fact -> fact.status() == TeamSuitabilityModels.FactStatus.FAIL)
                .filter(fact -> profile.teamConstraintMode(fact.code()) == TeamConstraintMode.HARD_FILTER)
                .map(fact -> switch (fact.code()) {
                    case MISSING_REQUIRED_INSTRUMENT -> HardFilterReasonCode.TEAM_MISSING_REQUIRED_INSTRUMENT;
                    case INSUFFICIENT_SKILL_COVERAGE -> HardFilterReasonCode.TEAM_INSUFFICIENT_SKILL_COVERAGE;
                    case LEAD_VOCAL_RANGE_MISMATCH -> HardFilterReasonCode.TEAM_LEAD_VOCAL_RANGE_MISMATCH;
                    case MISSING_VOCAL_CONFIGURATION -> HardFilterReasonCode.TEAM_MISSING_VOCAL_CONFIGURATION;
                    case UNAVAILABLE_ASSIGNED_MUSICIAN -> HardFilterReasonCode.TEAM_UNAVAILABLE_ASSIGNED_MUSICIAN;
                    case INCOMPLETE_TEAM -> HardFilterReasonCode.TEAM_INCOMPLETE;
                    case ASSIGNMENT_STATUS -> HardFilterReasonCode.TEAM_INCOMPLETE;
                    case OPTIONAL_INSTRUMENT_FIT -> HardFilterReasonCode.TEAM_MISSING_REQUIRED_INSTRUMENT;
                })
                .distinct()
                .toList();
    }

    private static List<HardFilterReasonCode> exclusionReasons(
            RecommendableArrangement candidate, String requiredLanguage, Set<UUID> excludedSongIds) {
        List<HardFilterReasonCode> reasons = new ArrayList<>();
        if (excludedSongIds.contains(candidate.songId())) {
            reasons.add(HardFilterReasonCode.EXCLUDED_BY_USER);
        }
        if (candidate.currentLyricsDocumentId() == null) {
            reasons.add(HardFilterReasonCode.MISSING_PROVENANCE);
        }
        if (candidate.approvalGateSummary() == null) {
            reasons.add(HardFilterReasonCode.MISSING_APPROVAL_SUMMARY);
        } else if (!allApprovalsApproved(candidate.approvalGateSummary())) {
            reasons.add(HardFilterReasonCode.FAILED_APPROVAL_GATE);
        }
        if (requiredLanguage != null
                && !requiredLanguage.isBlank()
                && (candidate.language() == null
                        || !candidate.language().toLowerCase(Locale.ROOT)
                                .equals(requiredLanguage.toLowerCase(Locale.ROOT)))) {
            reasons.add(HardFilterReasonCode.UNSUPPORTED_LANGUAGE);
        }
        if (candidate.musicalKey() == null || candidate.musicalKey().isBlank()) {
            reasons.add(HardFilterReasonCode.MISSING_KEY);
        }
        if (candidate.bpm() <= 0) {
            reasons.add(HardFilterReasonCode.MISSING_TEMPO);
        }
        return List.copyOf(reasons);
    }

    private static boolean allApprovalsApproved(ApprovalGateSummary summary) {
        return summary.songDoctrinalStatus() == ApprovalStatus.APPROVED
                && summary.songEditorialStatus() == ApprovalStatus.APPROVED
                && summary.songLicensingStatus() == ApprovalStatus.APPROVED
                && summary.arrangementMusicalStatus() == ApprovalStatus.APPROVED
                && summary.arrangementEditorialStatus() == ApprovalStatus.APPROVED
                && summary.lyricsDoctrinalStatus() == ApprovalStatus.APPROVED
                && summary.lyricsEditorialStatus() == ApprovalStatus.APPROVED
                && summary.lyricsLicensingStatus() == ApprovalStatus.APPROVED;
    }
}

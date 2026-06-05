package com.cadentia.reng.scoring;

import com.cadentia.generated.model.ExplicitRecommendationTeamConstraints;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.RecommendationArrangementTeamRequirement;
import com.cadentia.generated.model.RecommendationServiceTeamContext;
import com.cadentia.generated.model.RecommendationTeamAssignment;
import com.cadentia.generated.model.RecommendationTeamRequirementSlot;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.InstanceConfigurationProvider;
import com.cadentia.runtime.StaticInstanceConfigurationProvider;
import com.cadentia.reng.scoring.TeamSuitabilityModels.ArrangementTeamRequirement;
import com.cadentia.reng.scoring.TeamSuitabilityModels.AssignmentStatus;
import com.cadentia.reng.scoring.TeamSuitabilityModels.ExplicitTeamConstraints;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamAssignment;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamContextReference;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamRequirementSlot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ScoringRequestFactory {

    private static final String DEFAULT_LANGUAGE = "en";

    private final InstanceConfigurationProvider configurationProvider;

    public ScoringRequestFactory() {
        this(new StaticInstanceConfigurationProvider(InstanceConfiguration.localDevelopment(
                "local-development",
                "local",
                "cadentia-local-assets",
                "local-development",
                "env:CADENTIA_LOCAL_ASSET_KEY_REF",
                "cadentia:local:development",
                "local.development",
                List.of("local.development.audit-events", "local.development.recommendation-events"))));
    }

    public ScoringRequestFactory(InstanceConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    public ScoringRequest fromValidatedRequest(GenerateSetlistRequest request) {
        InstanceConfiguration.RecommendationPolicy defaults = configurationProvider.current().recommendationPolicy();
        SetlistCounts counts = request.getCounts();
        KeyPolicy keyPolicy = request.getKeyPolicy();
        TempoPolicy tempoPolicy = request.getTempoPolicy();

        boolean countsDefaulted = counts == null;
        boolean keyPolicyDefaulted = keyPolicy == null;
        boolean tempoPolicyDefaulted = tempoPolicy == null;
        boolean languageDefaulted = request.getLanguage() == null || request.getLanguage().isBlank();

        return new ScoringRequest(
                request.getVerseText(),
                request.getThemeHints() == null ? List.of() : request.getThemeHints(),
                countsDefaulted ? defaults.praiseCount() : counts.getPraise(),
                countsDefaulted ? defaults.worshipCount() : counts.getWorship(),
                keyPolicyDefaulted
                        ? new ScoringRequest.KeyPolicy(
                                defaults.keyPolicy().preferSameKey(),
                                defaults.keyPolicy().allowRelativeMajorMinor(),
                                defaults.keyPolicy().maxKeyCenters())
                        : new ScoringRequest.KeyPolicy(
                                Boolean.TRUE.equals(keyPolicy.getPreferSameKey()),
                                Boolean.TRUE.equals(keyPolicy.getAllowRelativeMajorMinor()),
                                keyPolicy.getMaxKeyCenters()),
                tempoPolicyDefaulted
                        ? new ScoringRequest.TempoPolicy(defaults.tempoPolicy().maxJumpBpm())
                        : new ScoringRequest.TempoPolicy(tempoPolicy.getMaxJumpBpm()),
                request.getEnergyArc() == null ? null : request.getEnergyArc().getValue(),
                languageDefaulted ? DEFAULT_LANGUAGE : request.getLanguage(),
                request.getExcludedSongs() == null ? List.of() : request.getExcludedSongs(),
                Boolean.TRUE.equals(request.getIncludeAdminDiagnostics())
                        && request.getExplanationAudience() == GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN,
                new ScoringRequest.DefaultsApplied(
                        countsDefaulted,
                        keyPolicyDefaulted,
                        tempoPolicyDefaulted,
                        languageDefaulted),
                teamContextReference(request.getServiceTeamContext()),
                explicitTeamConstraints(request.getTeamConstraints()));
    }

    private static TeamContextReference teamContextReference(RecommendationServiceTeamContext serviceTeamContext) {
        if (serviceTeamContext == null || serviceTeamContext.getServicePlanId() == null) {
            return null;
        }
        return new TeamContextReference(serviceTeamContext.getServicePlanId(), serviceTeamContext.getServicePlanBlockId());
    }

    private static ExplicitTeamConstraints explicitTeamConstraints(ExplicitRecommendationTeamConstraints constraints) {
        if (constraints == null) {
            return null;
        }
        Map<UUID, ArrangementTeamRequirement> requirements = constraints.getArrangementRequirements() == null
                ? Map.of()
                : constraints.getArrangementRequirements().entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(
                                entry -> UUID.fromString(entry.getKey()),
                                entry -> arrangementRequirement(entry.getValue())));
        return new ExplicitTeamConstraints(
                constraints.getServicePlanId(),
                constraints.getAssignments() == null
                        ? List.of()
                        : constraints.getAssignments().stream()
                                .map(ScoringRequestFactory::teamAssignment)
                                .toList(),
                requirements,
                Boolean.TRUE.equals(constraints.getIncompleteTeam()));
    }

    private static TeamAssignment teamAssignment(RecommendationTeamAssignment assignment) {
        return new TeamAssignment(
                assignment.getMusicianId(),
                AssignmentStatus.valueOf(assignment.getStatus().getValue()),
                Boolean.TRUE.equals(assignment.getAvailableForService()),
                assignment.getRoleCodes() == null ? Set.of() : Set.copyOf(assignment.getRoleCodes()),
                assignment.getInstrumentCodes() == null ? Set.of() : Set.copyOf(assignment.getInstrumentCodes()),
                assignment.getVocalPartCodes() == null ? Set.of() : Set.copyOf(assignment.getVocalPartCodes()),
                assignment.getInstrumentSkillRanks() == null ? Map.of() : Map.copyOf(assignment.getInstrumentSkillRanks()),
                assignment.getVocalSkillRanks() == null ? Map.of() : Map.copyOf(assignment.getVocalSkillRanks()),
                assignment.getComfortableLowMidiNote(),
                assignment.getComfortableHighMidiNote());
    }

    private static ArrangementTeamRequirement arrangementRequirement(RecommendationArrangementTeamRequirement requirement) {
        return new ArrangementTeamRequirement(
                requirement.getRequiredSlots() == null
                        ? List.of()
                        : requirement.getRequiredSlots().stream()
                                .map(ScoringRequestFactory::teamRequirementSlot)
                                .toList(),
                requirement.getOptionalSlots() == null
                        ? List.of()
                        : requirement.getOptionalSlots().stream()
                                .map(ScoringRequestFactory::teamRequirementSlot)
                                .toList(),
                requirement.getVocalConfiguration(),
                Boolean.TRUE.equals(requirement.getVocalConfigurationRequired()),
                requirement.getLeadVocalLowMidiNote(),
                requirement.getLeadVocalHighMidiNote(),
                requirement.getRequiredBackingVocalCount() == null ? 0 : requirement.getRequiredBackingVocalCount());
    }

    private static TeamRequirementSlot teamRequirementSlot(RecommendationTeamRequirementSlot slot) {
        return new TeamRequirementSlot(
                slot.getRoleCode(),
                slot.getInstrumentCode(),
                slot.getVocalPartCode(),
                slot.getMinimumSkillRank() == null ? 0 : slot.getMinimumSkillRank(),
                slot.getMinimumCount() == null ? 1 : slot.getMinimumCount());
    }
}

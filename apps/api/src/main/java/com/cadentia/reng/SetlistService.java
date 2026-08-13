package com.cadentia.reng;

import com.cadentia.api.controller.ServicePlanWorkflowSummaryResponseMapper;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.TagType;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.RecommendationExplanation;
import com.cadentia.generated.model.RecommendationExplanationCode;
import com.cadentia.generated.model.RecommendationExplanationEntry;
import com.cadentia.generated.model.RecommendationExplanationEvidence;
import com.cadentia.generated.model.RecommendationExplanationScope;
import com.cadentia.generated.model.RecommendationExplanationSubject;
import com.cadentia.generated.model.RecommendationRequestPolicySummary;
import com.cadentia.generated.model.RecommendationServiceTeamContext;
import com.cadentia.generated.model.RecommendationTieBreakFact;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.reng.scoring.CandidateFeatureScorer;
import com.cadentia.reng.scoring.DiagnosticsAudience;
import com.cadentia.reng.scoring.HardConstraintFilter;
import com.cadentia.reng.scoring.HardFilterResult;
import com.cadentia.reng.scoring.OrderedSetItem;
import com.cadentia.reng.scoring.OrderedSetResponse;
import com.cadentia.reng.scoring.RecommendationExplanationFact;
import com.cadentia.reng.scoring.ScoringRequest;
import com.cadentia.reng.scoring.ScoringRequestFactory;
import com.cadentia.reng.setlist.SetlistProposalPersistenceService;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummaryAudience;
import com.cadentia.rehearsal.RehearsalWorkflowSummaryProvider;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.InstanceConfigurationProvider;
import com.cadentia.runtime.RuntimeModuleAccessException;
import com.cadentia.runtime.StaticInstanceConfigurationProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SetlistService {
    private static final String STATUS_PROPOSED = "PROPOSED";
    private static final String STATUS_NO_APPROVED_CANDIDATES = "NO_APPROVED_CANDIDATES";
    private static final String STATUS_INSUFFICIENT_APPROVED_CANDIDATES = "INSUFFICIENT_APPROVED_CANDIDATES";

    private final InstanceConfigurationProvider configurationProvider;
    private final ScoringRequestFactory scoringRequestFactory;
    private final CandidateRetriever candidateRetriever;
    private final HardConstraintFilter hardConstraintFilter;
    private final CandidateFeatureScorer candidateFeatureScorer;
    private final SetOrderer setOrderer;
    private final RehearsalWorkflowSummaryProvider workflowSummaryService;
    private final SetlistProposalPersistenceService proposalPersistenceService;

    public SetlistService() {
        this(new StaticInstanceConfigurationProvider(InstanceConfiguration.localDevelopment(
                "local-development",
                "local",
                "cadentia-local-assets",
                "local-development",
                "env:CADENTIA_LOCAL_ASSET_KEY_REF",
                "cadentia:local:development",
                "local.development",
                List.of("local.development.audit-events", "local.development.recommendation-events"))),
                new ScoringRequestFactory(),
                null,
                new HardConstraintFilter(),
                new CandidateFeatureScorer(),
                new DeterministicSetOrderer(),
                null,
                null);
    }

    public SetlistService(
            InstanceConfigurationProvider configurationProvider,
            ScoringRequestFactory scoringRequestFactory) {
        this(configurationProvider, scoringRequestFactory, null);
    }

    @Autowired
    public SetlistService(
            InstanceConfigurationProvider configurationProvider,
            ScoringRequestFactory scoringRequestFactory,
            CandidateRetriever candidateRetriever,
            RehearsalWorkflowSummaryProvider workflowSummaryService,
            ObjectProvider<SetlistProposalPersistenceService> proposalPersistenceServiceProvider) {
        this(configurationProvider,
                scoringRequestFactory,
                candidateRetriever,
                new HardConstraintFilter(),
                new CandidateFeatureScorer(),
                new DeterministicSetOrderer(),
                workflowSummaryService,
                proposalPersistenceServiceProvider.getIfAvailable());
    }

    public SetlistService(
            CandidateRetriever candidateRetriever,
            RehearsalWorkflowSummaryProvider workflowSummaryService) {
        this(new StaticInstanceConfigurationProvider(InstanceConfiguration.localDevelopment(
                "local-development",
                "local",
                "cadentia-local-assets",
                "local-development",
                "env:CADENTIA_LOCAL_ASSET_KEY_REF",
                "cadentia:local:development",
                "local.development",
                List.of("local.development.audit-events", "local.development.recommendation-events"))),
                new ScoringRequestFactory(),
                candidateRetriever,
                new HardConstraintFilter(),
                new CandidateFeatureScorer(),
                new DeterministicSetOrderer(),
                workflowSummaryService,
                null);
    }

    public SetlistService(
            InstanceConfigurationProvider configurationProvider,
            ScoringRequestFactory scoringRequestFactory,
            RehearsalWorkflowSummaryProvider workflowSummaryService) {
        this(
                configurationProvider,
                scoringRequestFactory,
                null,
                new HardConstraintFilter(),
                new CandidateFeatureScorer(),
                new DeterministicSetOrderer(),
                workflowSummaryService,
                null);
    }

    SetlistService(
            InstanceConfigurationProvider configurationProvider,
            ScoringRequestFactory scoringRequestFactory,
            CandidateRetriever candidateRetriever,
            HardConstraintFilter hardConstraintFilter,
            CandidateFeatureScorer candidateFeatureScorer,
            SetOrderer setOrderer,
            RehearsalWorkflowSummaryProvider workflowSummaryService) {
        this(
                configurationProvider,
                scoringRequestFactory,
                candidateRetriever,
                hardConstraintFilter,
                candidateFeatureScorer,
                setOrderer,
                workflowSummaryService,
                null);
    }

    SetlistService(
            InstanceConfigurationProvider configurationProvider,
            ScoringRequestFactory scoringRequestFactory,
            CandidateRetriever candidateRetriever,
            HardConstraintFilter hardConstraintFilter,
            CandidateFeatureScorer candidateFeatureScorer,
            SetOrderer setOrderer,
            RehearsalWorkflowSummaryProvider workflowSummaryService,
            SetlistProposalPersistenceService proposalPersistenceService) {
        this.configurationProvider = configurationProvider;
        this.scoringRequestFactory = scoringRequestFactory;
        this.candidateRetriever = candidateRetriever;
        this.hardConstraintFilter = hardConstraintFilter;
        this.candidateFeatureScorer = candidateFeatureScorer;
        this.setOrderer = setOrderer;
        this.workflowSummaryService = workflowSummaryService;
        this.proposalPersistenceService = proposalPersistenceService;
    }

    public SetlistProposalResponse generate(GenerateSetlistRequest request) {
        InstanceConfiguration configuration = configurationProvider.current();
        if (!configuration.modules().recommendation()) {
            throw new RuntimeModuleAccessException("Recommendation module is disabled for this instance.");
        }
        if (!configuration.recommendationPolicy().requireApprovedOnly()
                || !configuration.recommendationPolicy().requireDatasetReferences()) {
            throw new RuntimeModuleAccessException(
                    "Recommendation policy must require approved catalog records and dataset references.");
        }

        ScoringRequest scoringRequest = scoringRequestFactory.fromValidatedRequest(request);
        String requestId = "req-" + UUID.randomUUID();
        String candidateSnapshotVersion = candidateSnapshotVersion(configuration, scoringRequest);
        List<RecommendableArrangement> retrieved = retrieveCandidates(scoringRequest);
        HardFilterResult filtered = hardConstraintFilter.filter(
                retrieved,
                scoringRequest,
                configuration.scoringProfile());
        List<CandidateFeatureScorer.CandidateFeatureScore> candidateScores = candidateFeatureScorer.scoreCandidates(
                filtered.eligibleCandidates(),
                scoringRequest,
                configuration.scoringProfile());
        OrderedSetResponse orderedSet = setOrderer.order(
                candidateScores,
                scoringRequest,
                configuration.scoringProfile(),
                candidateSnapshotVersion);
        String recommendationResultId = recommendationResultId(configuration, candidateSnapshotVersion, orderedSet);
        SetlistProposalResponse response = new SetlistProposalResponse()
                .status(status(scoringRequest, orderedSet))
                .requestId(requestId)
                .recommendationResultId(recommendationResultId)
                .auditMessages(auditMessages(configuration, scoringRequest, filtered, orderedSet))
                .explanation(explanation(
                        request,
                        scoringRequest,
                        orderedSet,
                        candidateByArrangementId(filtered.eligibleCandidates()),
                        requestId,
                        recommendationResultId));
        attachOperationalWorkflowSummary(response, request);
        persistGeneratedBaseline(request, response);
        return response;
    }

    private void persistGeneratedBaseline(GenerateSetlistRequest request, SetlistProposalResponse response) {
        if (proposalPersistenceService == null || response.getExplanation() == null) {
            return;
        }
        proposalPersistenceService.persistGeneratedBaseline(request, response)
                .ifPresent(snapshot -> {
                    response.getExplanation().setSetlistId(snapshot.setlistId().toString());
                    response.getExplanation().setSetlistVersionId(snapshot.versionId().toString());
                });
    }

    private List<RecommendableArrangement> retrieveCandidates(ScoringRequest request) {
        if (candidateRetriever == null) {
            return List.of();
        }
        return candidateRetriever.findCandidates(new CandidateSearchCriteria(
                request.language(),
                List.of(),
                null,
                null,
                null,
                null,
                requestedTagFilters(request),
                List.of(),
                ApprovalStatus.APPROVED));
    }

    private List<TagFilter> requestedTagFilters(ScoringRequest request) {
        List<TagFilter> filters = new ArrayList<>();
        request.themeHints().stream()
                .map(this::slug)
                .filter(value -> !value.isBlank())
                .map(slug -> TagFilter.bySlug(TagType.THEME, slug))
                .forEach(filters::add);
        if (request.verseText() != null && !request.verseText().isBlank()) {
            filters.add(TagFilter.bySlug(TagType.SCRIPTURE, slug(request.verseText())));
        }
        return filters.stream()
                .collect(java.util.stream.Collectors.toMap(
                        filter -> filter.tagType() + ":" + filter.slug(),
                        filter -> filter,
                        (first, ignored) -> first))
                .values()
                .stream()
                .toList();
    }

    private String status(ScoringRequest request, OrderedSetResponse orderedSet) {
        int selectedCount = orderedSet.items().size();
        if (selectedCount == 0) {
            return STATUS_NO_APPROVED_CANDIDATES;
        }
        if (selectedCount < request.praiseCount() + request.worshipCount()) {
            return STATUS_INSUFFICIENT_APPROVED_CANDIDATES;
        }
        return STATUS_PROPOSED;
    }

    private List<String> auditMessages(
            InstanceConfiguration configuration,
            ScoringRequest request,
            HardFilterResult filtered,
            OrderedSetResponse orderedSet) {
        List<String> messages = new ArrayList<>();
        messages.add("Recommendation Engine used approved catalog candidates for instance "
                + configuration.instanceId()
                + ".");
        messages.add("Scoring profile "
                + configuration.scoringProfile().version()
                + " selected "
                + orderedSet.items().size()
                + " of "
                + (request.praiseCount() + request.worshipCount())
                + " requested songs from "
                + filtered.eligibleCandidates().size()
                + " eligible approved candidates.");
        if (orderedSet.items().isEmpty()) {
            messages.add("No approved eligible catalog candidates matched the request; no songs were fabricated.");
        } else if (orderedSet.items().size() < request.praiseCount() + request.worshipCount()) {
            messages.add("Insufficient approved eligible candidates were available; the response may contain a partial proposal only.");
        }
        return List.copyOf(messages);
    }

    private RecommendationExplanation explanation(
            GenerateSetlistRequest request,
            ScoringRequest scoringRequest,
            OrderedSetResponse orderedSet,
            Map<UUID, RecommendableArrangement> candidatesByArrangementId,
            String requestId,
            String recommendationResultId) {
        return new RecommendationExplanation()
                .schemaName(RecommendationExplanation.SchemaNameEnum.RECOMMENDATION_EXPLANATION)
                .schemaVersion(RecommendationExplanation.SchemaVersionEnum.RECOMMENDATION_EXPLANATION_V1)
                .generatedBy(RecommendationExplanation.GeneratedByEnum.RECOMMENDATION_ENGINE)
                .requestId(requestId)
                .recommendationResultId(recommendationResultId)
                .scoringProfileVersion(orderedSet.scoringProfileVersion())
                .catalogSnapshotVersion(orderedSet.candidateSnapshotVersion())
                .generatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .requestPolicySummary(requestPolicySummary(request, scoringRequest))
                .deterministicTieBreaks(tieBreaks(orderedSet))
                .selectedSongs(selectedSongEntries(orderedSet, candidatesByArrangementId))
                .adjacentTransitions(mapFacts(flattenTransitionFacts(orderedSet), RecommendationExplanationScope.ADJACENT_TRANSITION))
                .setLevel(mapFacts(orderedSet.setExplanationFacts(), RecommendationExplanationScope.SET_LEVEL))
                .warnings(warnings(scoringRequest, orderedSet))
                .diagnostics(List.of());
    }

    private RecommendationRequestPolicySummary requestPolicySummary(
            GenerateSetlistRequest request,
            ScoringRequest scoringRequest) {
        return new RecommendationRequestPolicySummary()
                .counts(new SetlistCounts(scoringRequest.praiseCount(), scoringRequest.worshipCount()))
                .keyPolicy(new com.cadentia.generated.model.KeyPolicy(
                        scoringRequest.keyPolicy().preferSameKey(),
                        scoringRequest.keyPolicy().allowRelativeMajorMinor(),
                        scoringRequest.keyPolicy().maxKeyCenters()))
                .tempoPolicy(new TempoPolicy(scoringRequest.tempoPolicy().maxJumpBpm()))
                .themeHints(scoringRequest.themeHints())
                .scriptureReferences(request.getScriptureReferences() == null ? List.of() : request.getScriptureReferences())
                .language(scoringRequest.language())
                .energyArc(scoringRequest.energyArc())
                .serviceMoment(request.getServiceMoment() == null ? null : request.getServiceMoment().getValue())
                .appliedDefaults(appliedDefaults(scoringRequest.defaultsApplied()));
    }

    private List<String> appliedDefaults(ScoringRequest.DefaultsApplied defaults) {
        List<String> applied = new ArrayList<>();
        if (defaults.countsDefaulted()) {
            applied.add("counts");
        }
        if (defaults.keyPolicyDefaulted()) {
            applied.add("keyPolicy");
        }
        if (defaults.tempoPolicyDefaulted()) {
            applied.add("tempoPolicy");
        }
        if (defaults.languageDefaulted()) {
            applied.add("language");
        }
        return List.copyOf(applied);
    }

    private List<RecommendationTieBreakFact> tieBreaks(OrderedSetResponse orderedSet) {
        List<String> affectedIds = orderedSet.items().stream()
                .map(item -> item.arrangementId().toString())
                .toList();
        if (affectedIds.isEmpty()) {
            return List.of();
        }
        return orderedSet.deterministicOrderingRules().stream()
                .map(rule -> new RecommendationTieBreakFact(
                        rule,
                        RecommendationTieBreakFact.DirectionEnum.ASC,
                        affectedIds,
                        Map.of("candidateSnapshotVersion", orderedSet.candidateSnapshotVersion()),
                        RecommendationExplanationCode.DETERMINISTIC_TIE_BREAK_APPLIED))
                .toList();
    }

    private List<RecommendationExplanationEntry> selectedSongEntries(
            OrderedSetResponse orderedSet,
            Map<UUID, RecommendableArrangement> candidatesByArrangementId) {
        return orderedSet.items().stream()
                .map(item -> selectedSongEntry(item, candidatesByArrangementId.get(item.arrangementId())))
                .toList();
    }

    private RecommendationExplanationEntry selectedSongEntry(
            OrderedSetItem item,
            RecommendableArrangement candidate) {
        String title = candidate == null ? "Approved selection " + item.songId() : candidate.title();
        return new RecommendationExplanationEntry(
                RecommendationExplanationCode.APPROVAL_ELIGIBLE,
                RecommendationExplanationEntry.SeverityEnum.INFO,
                RecommendationExplanationScope.SELECTED_SONG,
                RecommendationExplanationEntry.AudienceEnum.PUBLIC,
                new RecommendationExplanationSubject(
                        RecommendationExplanationSubject.TypeEnum.SONG,
                        item.songId().toString())
                        .itemId(String.valueOf(item.position()))
                        .songId(item.songId().toString())
                        .arrangementId(item.arrangementId().toString()),
                "selected.approved",
                Map.of("position", item.position(), "title", title),
                selectedEvidence(item, candidate))
                .defaultText(title)
                .scoreImpact(item.candidateTotalScore());
    }

    private List<RecommendationExplanationEvidence> selectedEvidence(
            OrderedSetItem item,
            RecommendableArrangement candidate) {
        List<RecommendationExplanationEvidence> evidence = new ArrayList<>();
        evidence.add(new RecommendationExplanationEvidence(
                RecommendationExplanationEvidence.TypeEnum.CATALOG,
                "catalog:arrangement:" + item.arrangementId()));
        evidence.add(new RecommendationExplanationEvidence(
                RecommendationExplanationEvidence.TypeEnum.CATALOG,
                "catalog:song:" + item.songId()));
        evidence.add(new RecommendationExplanationEvidence(
                RecommendationExplanationEvidence.TypeEnum.APPROVAL,
                "approval:approval_gate_summary"));
        if (candidate != null && candidate.currentLyricsDocumentId() != null) {
            evidence.add(new RecommendationExplanationEvidence(
                    RecommendationExplanationEvidence.TypeEnum.PROVENANCE,
                    "lyrics_document:" + candidate.currentLyricsDocumentId()));
        }
        return List.copyOf(evidence);
    }

    private List<RecommendationExplanationEntry> warnings(
            ScoringRequest scoringRequest,
            OrderedSetResponse orderedSet) {
        int requested = scoringRequest.praiseCount() + scoringRequest.worshipCount();
        if (orderedSet.items().size() >= requested) {
            return List.of();
        }
        return List.of(new RecommendationExplanationEntry(
                RecommendationExplanationCode.INSUFFICIENT_CANDIDATES,
                RecommendationExplanationEntry.SeverityEnum.WARNING,
                RecommendationExplanationScope.WARNING,
                RecommendationExplanationEntry.AudienceEnum.PUBLIC,
                new RecommendationExplanationSubject(RecommendationExplanationSubject.TypeEnum.SET, "recommended-set"),
                "set.insufficient_candidates",
                Map.of("requested", requested, "selected", orderedSet.items().size()),
                List.of(new RecommendationExplanationEvidence(
                        RecommendationExplanationEvidence.TypeEnum.POLICY,
                        "policy:approved_only"))));
    }

    private List<RecommendationExplanationFact> flattenTransitionFacts(
            OrderedSetResponse orderedSet) {
        return orderedSet.items().stream()
                .flatMap(item -> item.explanationFacts().stream())
                .filter(fact -> "adjacent_transition".equals(fact.scope()))
                .toList();
    }

    private List<RecommendationExplanationEntry> mapFacts(
            List<RecommendationExplanationFact> facts,
            RecommendationExplanationScope fallbackScope) {
        return facts.stream()
                .sorted(Comparator.comparing(RecommendationExplanationFact::code)
                        .thenComparing(RecommendationExplanationFact::templateKey))
                .map(fact -> mapFact(fact, fallbackScope))
                .toList();
    }

    private RecommendationExplanationEntry mapFact(
            RecommendationExplanationFact fact,
            RecommendationExplanationScope fallbackScope) {
        return new RecommendationExplanationEntry(
                explanationCode(fact.code()),
                severity(fact.severity()),
                scope(fact.scope(), fallbackScope),
                RecommendationExplanationEntry.AudienceEnum.PUBLIC,
                subject(fact.subject()),
                fact.templateKey(),
                fact.values(),
                fact.evidence().stream().map(this::evidence).toList())
                .scoreImpact(fact.scoreImpact());
    }

    private RecommendationExplanationCode explanationCode(String code) {
        try {
            return RecommendationExplanationCode.fromValue(code);
        } catch (IllegalArgumentException ex) {
            return RecommendationExplanationCode.LOW_CONFIDENCE_METADATA_PRESENT;
        }
    }

    private RecommendationExplanationEntry.SeverityEnum severity(String severity) {
        return switch (normalize(severity)) {
            case "warning" -> RecommendationExplanationEntry.SeverityEnum.WARNING;
            case "blocked" -> RecommendationExplanationEntry.SeverityEnum.BLOCKED;
            default -> RecommendationExplanationEntry.SeverityEnum.INFO;
        };
    }

    private RecommendationExplanationScope scope(String scope, RecommendationExplanationScope fallback) {
        try {
            return RecommendationExplanationScope.fromValue(scope);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private RecommendationExplanationSubject subject(
            com.cadentia.reng.scoring.RecommendationExplanationSubject subject) {
        if (subject == null) {
            return new RecommendationExplanationSubject(RecommendationExplanationSubject.TypeEnum.SET, "recommended-set");
        }
        RecommendationExplanationSubject.TypeEnum type = switch (normalize(subject.type())) {
            case "song" -> RecommendationExplanationSubject.TypeEnum.SONG;
            case "arrangement" -> RecommendationExplanationSubject.TypeEnum.ARRANGEMENT;
            case "transition" -> RecommendationExplanationSubject.TypeEnum.TRANSITION;
            case "candidate" -> RecommendationExplanationSubject.TypeEnum.CANDIDATE;
            case "policy" -> RecommendationExplanationSubject.TypeEnum.POLICY;
            case "catalog" -> RecommendationExplanationSubject.TypeEnum.CATALOG;
            default -> RecommendationExplanationSubject.TypeEnum.SET;
        };
        return new RecommendationExplanationSubject(type, subject.id())
                .sourceItemId(subject.sourceId())
                .targetItemId(subject.targetId());
    }

    private RecommendationExplanationEvidence evidence(
            com.cadentia.reng.scoring.RecommendationExplanationEvidence evidence) {
        RecommendationExplanationEvidence.TypeEnum type = switch (normalize(evidence.type())) {
            case "request" -> RecommendationExplanationEvidence.TypeEnum.REQUEST;
            case "score" -> RecommendationExplanationEvidence.TypeEnum.SCORE;
            case "transition" -> RecommendationExplanationEvidence.TypeEnum.TRANSITION;
            case "approval" -> RecommendationExplanationEvidence.TypeEnum.APPROVAL;
            case "provenance" -> RecommendationExplanationEvidence.TypeEnum.PROVENANCE;
            case "policy" -> RecommendationExplanationEvidence.TypeEnum.POLICY;
            case "diagnostic" -> RecommendationExplanationEvidence.TypeEnum.DIAGNOSTIC;
            default -> RecommendationExplanationEvidence.TypeEnum.CATALOG;
        };
        return new RecommendationExplanationEvidence(type, evidence.ref())
                .field(evidence.field())
                .confidence(evidence.confidence());
    }

    private Map<UUID, RecommendableArrangement> candidateByArrangementId(List<RecommendableArrangement> candidates) {
        Map<UUID, RecommendableArrangement> byId = new HashMap<>();
        for (RecommendableArrangement candidate : candidates) {
            byId.put(candidate.arrangementId(), candidate);
        }
        return Map.copyOf(byId);
    }

    private String candidateSnapshotVersion(InstanceConfiguration configuration, ScoringRequest request) {
        return "approved-catalog:"
                + configuration.instanceId()
                + ":"
                + normalize(request.language())
                + ":"
                + Integer.toHexString(requestedTagFilters(request).hashCode());
    }

    private String recommendationResultId(
            InstanceConfiguration configuration,
            String candidateSnapshotVersion,
            OrderedSetResponse orderedSet) {
        String selected = orderedSet.items().stream()
                .map(item -> item.arrangementId().toString())
                .collect(java.util.stream.Collectors.joining(","));
        return "rec-"
                + Integer.toHexString((configuration.instanceId()
                        + "|"
                        + candidateSnapshotVersion
                        + "|"
                        + orderedSet.scoringProfileVersion()
                        + "|"
                        + selected).hashCode());
    }

    private String slug(String value) {
        return normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void attachOperationalWorkflowSummary(SetlistProposalResponse response, GenerateSetlistRequest request) {
        if (workflowSummaryService == null || request == null) {
            return;
        }
        RecommendationServiceTeamContext context = request.getServiceTeamContext();
        if (context == null || context.getServicePlanId() == null) {
            return;
        }
        response.setOperationalWorkflowSummary(ServicePlanWorkflowSummaryResponseMapper.toResponse(
                workflowSummaryService.summarize(context.getServicePlanId(), workflowAudience(request))));
    }

    private RehearsalWorkflowSummaryAudience workflowAudience(GenerateSetlistRequest request) {
        DiagnosticsAudience audience = DiagnosticsAudience.fromWireValue(
                request.getExplanationAudience() == null ? null : request.getExplanationAudience().getValue());
        return switch (audience) {
            case ADMIN -> RehearsalWorkflowSummaryAudience.ADMIN;
            case WORSHIP_LEADER -> RehearsalWorkflowSummaryAudience.WORSHIP_LEADER;
            case PUBLIC -> RehearsalWorkflowSummaryAudience.PUBLIC;
        };
    }
}

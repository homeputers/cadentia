package com.cadentia.api.controller;

import com.cadentia.api.controller.ConversationSessionRecord.ConversationSessionSourceStamp;
import com.cadentia.generated.model.ChannelType;
import com.cadentia.generated.model.ConversationClarificationRequest;
import com.cadentia.generated.model.ConversationConfirmRequest;
import com.cadentia.generated.model.ConversationRecoveryResponse;
import com.cadentia.generated.model.ConversationRevisionEvent;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.ConversationSlotSource;
import com.cadentia.generated.model.ConversationSlotUpdateRequest;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.ConversationSlotUpdateRequestSlotPatch;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.intent.Counts;
import com.cadentia.intent.DefaultSessionMergeService;
import com.cadentia.intent.IntentKeyPolicy;
import com.cadentia.intent.IntentTempoPolicy;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.IntentType;
import com.cadentia.intent.ValidatedIntent;
import com.cadentia.intent.SessionMergeResult;
import com.cadentia.intent.SessionSlotUpdate;
import com.cadentia.intent.SlotValueSource;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConversationSessionFacade {
    private final DefaultSessionMergeService mergeService;
    private final ValidatedSetlistRequestMapper mapper;
    private final Duration inactivityTimeout;
    private final Duration absoluteLifetime;
    private final IntentService intentService;
    private final ConversationSessionRepository repository;

    @Autowired
    public ConversationSessionFacade(
            DefaultSessionMergeService mergeService,
            ValidatedSetlistRequestMapper mapper,
            @Value("${cadentia.conversation.session.inactivity-timeout:PT30M}") Duration inactivityTimeout,
            @Value("${cadentia.conversation.session.absolute-lifetime:PT4H}") Duration absoluteLifetime,
            ObjectProvider<ConversationSessionRepository> repositoryProvider,
            ObjectProvider<IntentService> intentServiceProvider) {
        this.mergeService = mergeService;
        this.mapper = mapper;
        this.inactivityTimeout = inactivityTimeout;
        this.absoluteLifetime = absoluteLifetime;
        this.repository = repositoryProvider.getIfAvailable(InMemoryConversationSessionRepository::new);
        this.intentService = intentServiceProvider.getIfAvailable();
    }

    public ConversationSessionFacade(
            DefaultSessionMergeService mergeService,
            ValidatedSetlistRequestMapper mapper,
            Duration inactivityTimeout,
            Duration absoluteLifetime) {
        this(mergeService, mapper, inactivityTimeout, absoluteLifetime, new InMemoryConversationSessionRepository(), null);
    }

    public ConversationSessionFacade(
            DefaultSessionMergeService mergeService,
            ValidatedSetlistRequestMapper mapper,
            Duration inactivityTimeout,
            Duration absoluteLifetime,
            IntentService intentService) {
        this(mergeService, mapper, inactivityTimeout, absoluteLifetime, new InMemoryConversationSessionRepository(), intentService);
    }

    public ConversationSessionFacade(
            DefaultSessionMergeService mergeService,
            ValidatedSetlistRequestMapper mapper,
            Duration inactivityTimeout,
            Duration absoluteLifetime,
            ConversationSessionRepository repository,
            IntentService intentService) {
        this.mergeService = mergeService;
        this.mapper = mapper;
        this.inactivityTimeout = inactivityTimeout;
        this.absoluteLifetime = absoluteLifetime;
        this.repository = repository;
        this.intentService = intentService;
    }

    public ConversationSessionStateResponse get(UUID sessionId) {
        ConversationSessionRecord record = currentRecord(sessionId, ConversationState.COLLECTING);
        return snapshot(sessionId, record, List.of("Session state emitted."));
    }

    public ConversationSessionStateResponse startNew(UUID sessionId) {
        Optional<ConversationSessionRecord> prior = repository.findById(sessionId);
        OffsetDateTime now = OffsetDateTime.now();
        List<ConversationRevisionEvent> history = prior.map(ConversationSessionRecord::revisionHistory).orElse(List.of());
        if (prior.isPresent()) {
            history = appendRevisions(history, revision(
                    ConversationRevisionEvent.EventTypeEnum.CANCEL,
                    SlotValueSource.USER_EDIT,
                    "Prior session discarded for new setlist flow."));
        }
        ConversationSessionRecord fresh = new ConversationSessionRecord(
                sessionId,
                ConversationState.COLLECTING,
                empty(),
                defaultSlotSources(now),
                history,
                now,
                now,
                null,
                ChannelType.MIXED.getValue(),
                null,
                null,
                Map.of());
        repository.save(fresh);
        return snapshot(sessionId, fresh, List.of("New setlist flow started."));
    }

    public ConversationSessionStateResponse clarify(UUID sessionId, ConversationClarificationRequest request) {
        ConversationSessionRecord record = transition(
                currentRecord(sessionId, ConversationState.COLLECTING),
                ConversationState.CLARIFICATION_REQUIRED,
                revision(ConversationRevisionEvent.EventTypeEnum.CLARIFICATION_ANSWER,
                        SlotValueSource.USER_EDIT,
                        "Clarification requested."));
        repository.save(record);
        return snapshot(sessionId, record, List.of("Clarification requested."));
    }

    public ConversationSessionStateResponse confirm(UUID sessionId, ConversationConfirmRequest request) {
        ConversationSessionRecord baseline = currentRecord(sessionId, ConversationState.COLLECTING);
        if (baseline.state() != ConversationState.READY_TO_CONFIRM) {
            return snapshot(
                    sessionId,
                    baseline,
                    List.of("Session is not ready to confirm. Provide a valid request before recommendation."));
        }
        ConversationSessionRecord record = transition(
                baseline,
                ConversationState.CONFIRMED,
                revision(ConversationRevisionEvent.EventTypeEnum.CONFIRM,
                        SlotValueSource.USER_EDIT,
                        "Session confirmed."));
        repository.save(record);
        return snapshot(sessionId, record, List.of("Session confirmed."));
    }

    public ConversationSessionStateResponse cancel(UUID sessionId) {
        ConversationSessionRecord record = transition(
                currentRecord(sessionId, ConversationState.COLLECTING),
                ConversationState.CANCELLED,
                revision(ConversationRevisionEvent.EventTypeEnum.CANCEL,
                        SlotValueSource.USER_EDIT,
                        "Session cancelled."));
        repository.save(record);
        return snapshot(sessionId, record, List.of("Session cancelled."));
    }

    public ConversationSessionStateResponse revise(UUID sessionId) {
        ConversationSessionRecord baseline = currentRecord(sessionId, ConversationState.COLLECTING);
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, ConversationSessionSourceStamp> resetSources = defaultSlotSources(now);
        ConversationSessionRecord revised = baseline.withSlots(
                ConversationState.COLLECTING,
                baseline.slots(),
                resetSources,
                appendRevisions(baseline.revisionHistory(), revision(
                        ConversationRevisionEvent.EventTypeEnum.SLOT_UPDATE,
                        SlotValueSource.USER_EDIT,
                        "Session revised for reconfiguration.")),
                now);
        repository.save(revised);
        return snapshot(sessionId, revised, List.of("Session revised. Reconfigure your setlist constraints."));
    }

    public ConversationSessionStateResponse update(UUID sessionId, ConversationSlotUpdateRequest request) {
        ConversationSessionRecord baselineRecord = currentRecord(sessionId, ConversationState.COLLECTING);
        if (request.getSlotPatch() == null) {
            return snapshot(
                    sessionId,
                    baselineRecord,
                    List.of("Session slots unchanged; no patch was provided."));
        }
        GenerateSetlistSlots baseline = baselineRecord.slots();
        GenerateSetlistSlots patch = toSlots(request.getSlotPatch(), baseline);
        SlotValueSource updateSource = SlotValueSource.valueOf(request.getSource().getValue().toUpperCase());
        SessionMergeResult merged = mergeService.merge(
                baseline,
                new SessionSlotUpdate(
                        patch,
                        updateSource,
                        false));
        OffsetDateTime updatedAt = OffsetDateTime.now();
        ConversationSessionRecord updated = baselineRecord.withSlots(
                ConversationState.COLLECTING,
                merged.mergedSlots(),
                mergedSources(baselineRecord.slotSources(), merged, updatedAt),
                appendRevisions(baselineRecord.revisionHistory(), mergeRevisions(merged, updatedAt)),
                updatedAt);
        repository.save(updated);
        return snapshot(sessionId, updated, List.of("Session slots updated via deterministic merge."));
    }

    public ConversationSessionStateResponse ingestFreeText(UUID sessionId, String text) {
        if (intentService == null) {
            return update(sessionId, new ConversationSlotUpdateRequest().source(com.cadentia.generated.model.SlotValueSource.FREE_TEXT));
        }
        IntentParseResult parseResult = intentService.parse(text);
        ValidatedIntent parsedIntent = parseResult.intent();
        if (parsedIntent.intentType() == IntentType.CLARIFY_REQUEST) {
            ConversationSessionRecord record = transition(
                    currentRecord(sessionId, ConversationState.COLLECTING),
                    ConversationState.CLARIFICATION_REQUIRED,
                    revision(ConversationRevisionEvent.EventTypeEnum.CLARIFICATION_ANSWER,
                            SlotValueSource.FREE_TEXT,
                            "Intent extraction requested clarification."));
            repository.save(record);
            return snapshot(sessionId, record, List.of("Intent extraction requested clarification before recommendation."));
        }
        if (parsedIntent.intentType() == IntentType.UNSUPPORTED_REQUEST) {
            ConversationSessionRecord record = transition(
                    currentRecord(sessionId, ConversationState.COLLECTING),
                    ConversationState.CANCELLED,
                    revision(ConversationRevisionEvent.EventTypeEnum.CANCEL,
                            SlotValueSource.FREE_TEXT,
                            "Intent extraction safely rejected the request."));
            repository.save(record);
            return snapshot(sessionId, record, List.of("Intent extraction safely rejected the request before recommendation."));
        }
        GenerateSetlistIntent intent = (GenerateSetlistIntent) parsedIntent;
        ConversationSessionRecord baselineRecord = currentRecord(sessionId, ConversationState.COLLECTING);
        SessionMergeResult merged = mergeService.merge(
                baselineRecord.slots(),
                new SessionSlotUpdate(intent.slots(), SlotValueSource.FREE_TEXT, true));
        OffsetDateTime updatedAt = OffsetDateTime.now();
        ConversationSessionRecord updated = baselineRecord.withSlots(
                ConversationState.READY_TO_CONFIRM,
                merged.mergedSlots(),
                mergedSources(baselineRecord.slotSources(), merged, updatedAt),
                appendRevisions(baselineRecord.revisionHistory(), mergeRevisions(merged, updatedAt)),
                updatedAt);
        repository.save(updated);
        return snapshot(sessionId, updated, List.of("Free-text request was validated by the shared intent boundary."));
    }

    public ConversationRecoveryResponse recover(UUID sessionId) {
        ConversationSessionRecord prior = currentRecord(sessionId, ConversationState.COLLECTING);
        if (prior.state() == ConversationState.CONFIRMED) {
            ConversationSessionStateResponse recoveredState = snapshot(
                    sessionId,
                    prior,
                    List.of("Confirmed session recovered without restarting."));
            return new ConversationRecoveryResponse(
                    sessionId,
                    prior.state(),
                    recoveredState,
                    "Session was already confirmed. Continue from the generated setlist.",
                    false);
        }
        if (prior.state() == ConversationState.CANCELLED) {
            ConversationSessionStateResponse recoveredState = snapshot(
                    sessionId,
                    prior,
                    List.of("Cancelled session recovered without restarting."));
            return new ConversationRecoveryResponse(
                    sessionId,
                    prior.state(),
                    recoveredState,
                    "Session was cancelled. Start a new /newsetlist flow to continue.",
                    true);
        }
        if (prior.state() != ConversationState.EXPIRED) {
            ConversationSessionStateResponse recoveredState = snapshot(
                    sessionId,
                    prior,
                    List.of("Active session recovered without restarting."));
            return new ConversationRecoveryResponse(
                    sessionId,
                    prior.state(),
                    recoveredState,
                    "Session is still active. Continue the current flow.",
                    false);
        }

        ConversationSessionRecord expired = transition(
                prior,
                ConversationState.EXPIRED,
                revision(ConversationRevisionEvent.EventTypeEnum.EXPIRE,
                        SlotValueSource.DEFAULT,
                        "Session expired before recovery."));
        repository.save(expired);

        OffsetDateTime restartedAt = OffsetDateTime.now();
        ConversationSessionRecord restarted = new ConversationSessionRecord(
                sessionId,
                ConversationState.START,
                empty(),
                defaultSlotSources(restartedAt),
                appendRevisions(expired.revisionHistory(), revision(
                        ConversationRevisionEvent.EventTypeEnum.RECOVER,
                        SlotValueSource.USER_EDIT,
                        "Session recovered with a fresh request state.")),
                restartedAt,
                restartedAt,
                null,
                expired.channel(),
                expired.channelUpdateId(),
                expired.recommendationResultId(),
                expired.correlationMetadata());
        repository.save(restarted);

        ConversationSessionStateResponse recoveredState = snapshot(
                sessionId,
                restarted,
                List.of(
                        "Session expired and was restarted.",
                        "Lost context summary: all unconfirmed slot constraints were discarded.",
                        "Retained context summary: immutable session identifier and revision history remain auditable."));
        return new ConversationRecoveryResponse(
                sessionId,
                expired.state(),
                recoveredState,
                "Session expired. Start over or re-enter your previous constraints.",
                true);
    }

    public void recordChannelCorrelation(UUID sessionId, String channel, String channelUpdateId, String correlationId) {
        ConversationSessionRecord record = currentRecord(sessionId, ConversationState.COLLECTING);
        repository.save(record.withCorrelation(
                channel,
                channelUpdateId,
                null,
                mergedCorrelationMetadata(record.correlationMetadata(), "correlationId", correlationId),
                OffsetDateTime.now()));
    }

    public void recordRecommendationCorrelation(UUID sessionId, String recommendationResultId) {
        recordRecommendationCorrelation(sessionId, recommendationResultId, null);
    }

    public void recordRecommendationCorrelation(UUID sessionId, String recommendationResultId, String setlistId) {
        recordRecommendationCorrelation(sessionId, recommendationResultId, setlistId, null);
    }

    public void recordRecommendationCorrelation(
            UUID sessionId,
            String recommendationResultId,
            String setlistId,
            String setlistVersionId) {
        ConversationSessionRecord record = currentRecord(sessionId, ConversationState.COLLECTING);
        repository.save(record.withCorrelation(
                null,
                null,
                recommendationResultId,
                mergedCorrelationMetadata(record.correlationMetadata(), Map.of(
                        "setlistId", nullToEmpty(setlistId),
                        "setlistVersionId", nullToEmpty(setlistVersionId))),
                OffsetDateTime.now()));
    }

    private Map<String, String> mergedCorrelationMetadata(
            Map<String, String> currentMetadata,
            String key,
            String value) {
        return mergedCorrelationMetadata(currentMetadata, Map.of(key, nullToEmpty(value)));
    }

    private Map<String, String> mergedCorrelationMetadata(
            Map<String, String> currentMetadata,
            Map<String, String> nextMetadata) {
        Map<String, String> metadata = new HashMap<>(currentMetadata);
        nextMetadata.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                metadata.put(key, value);
            }
        });
        return Map.copyOf(metadata);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private ConversationSessionRecord currentRecord(UUID sessionId, ConversationState defaultState) {
        ConversationSessionRecord record = repository.findById(sessionId)
                .orElseGet(() -> repository.save(newRecord(sessionId, defaultState)));
        if (record.state() == ConversationState.EXPIRED
                || record.state() == ConversationState.CONFIRMED
                || record.state() == ConversationState.CANCELLED) {
            return record;
        }
        if (isExpired(record)) {
            ConversationSessionRecord expired = transition(
                    record,
                    ConversationState.EXPIRED,
                    revision(ConversationRevisionEvent.EventTypeEnum.EXPIRE,
                            SlotValueSource.DEFAULT,
                            "Session expired."));
            repository.save(expired);
            return expired;
        }
        return record;
    }

    private ConversationSessionRecord newRecord(UUID sessionId, ConversationState state) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ConversationSessionRecord(
                sessionId,
                state,
                empty(),
                defaultSlotSources(now),
                List.of(),
                now,
                now,
                null,
                ChannelType.MIXED.getValue(),
                null,
                null,
                Map.of());
    }

    private boolean isExpired(ConversationSessionRecord record) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean inactive = record.updatedAt().plus(inactivityTimeout).isBefore(now);
        boolean tooOld = record.createdAt().plus(absoluteLifetime).isBefore(now);
        return inactive || tooOld;
    }

    private ConversationSessionRecord transition(
            ConversationSessionRecord record,
            ConversationState targetState,
            ConversationRevisionEvent revision) {
        OffsetDateTime updatedAt = OffsetDateTime.now();
        return record.withState(
                targetState,
                appendRevisions(record.revisionHistory(), revision),
                updatedAt,
                targetState == ConversationState.CONFIRMED ? updatedAt : record.confirmedAt());
    }

    private ConversationSessionStateResponse snapshot(UUID sessionId, ConversationSessionRecord record, List<String> messages) {
        GenerateSetlistRequest slots = mapper.toGenerateSetlistRequest(new GenerateSetlistIntent("v1", record.slots()));
        OffsetDateTime expiresAt = record.updatedAt().plus(inactivityTimeout);
        return new ConversationSessionStateResponse(
                sessionId,
                record.state(),
                ChannelType.MIXED,
                slots,
                slotSources(record),
                new ArrayList<>(record.revisionHistory()),
                expiresAt,
                messages)
                .confirmedAt(record.confirmedAt())
                .recommendationResultId(record.recommendationResultId())
                .setlistId(record.correlationMetadata().get("setlistId"))
                .setlistVersionId(record.correlationMetadata().get("setlistVersionId"));
    }

    private Map<String, ConversationSessionSourceStamp> mergedSources(
            Map<String, ConversationSessionSourceStamp> baselineSources,
            SessionMergeResult merged,
            OffsetDateTime updatedAt) {
        Map<String, ConversationSessionSourceStamp> sources = new HashMap<>(baselineSources);
        merged.slotSources().forEach((slot, source) -> sources.put(slot, new ConversationSessionSourceStamp(source, updatedAt)));
        return Map.copyOf(sources);
    }

    private Map<String, ConversationSessionSourceStamp> defaultSlotSources(OffsetDateTime updatedAt) {
        return Map.of(
                "counts", new ConversationSessionSourceStamp(SlotValueSource.DEFAULT, updatedAt),
                "keyPolicy", new ConversationSessionSourceStamp(SlotValueSource.DEFAULT, updatedAt),
                "tempoPolicy", new ConversationSessionSourceStamp(SlotValueSource.DEFAULT, updatedAt));
    }

    private List<ConversationRevisionEvent> mergeRevisions(SessionMergeResult merged, OffsetDateTime occurredAt) {
        return merged.events().stream()
                .map(event -> revision(
                        ConversationRevisionEvent.EventTypeEnum.SLOT_UPDATE,
                        event.source(),
                        "Updated " + event.slotPath() + " from " + event.source().name().toLowerCase() + ".",
                        occurredAt))
                .toList();
    }

    private List<ConversationRevisionEvent> appendRevisions(
            List<ConversationRevisionEvent> prior,
            List<ConversationRevisionEvent> next) {
        List<ConversationRevisionEvent> revisions = new ArrayList<>(prior);
        revisions.addAll(next);
        return List.copyOf(revisions);
    }

    private List<ConversationRevisionEvent> appendRevisions(
            List<ConversationRevisionEvent> prior,
            ConversationRevisionEvent next) {
        return appendRevisions(prior, List.of(next));
    }

    private ConversationRevisionEvent revision(
            ConversationRevisionEvent.EventTypeEnum eventType,
            SlotValueSource source,
            String summary) {
        return revision(eventType, source, summary, OffsetDateTime.now());
    }

    private ConversationRevisionEvent revision(
            ConversationRevisionEvent.EventTypeEnum eventType,
            SlotValueSource source,
            String summary,
            OffsetDateTime occurredAt) {
        return new ConversationRevisionEvent(
                UUID.randomUUID(),
                eventType,
                toApiSource(source),
                occurredAt)
                .summary(summary);
    }

    private List<ConversationSlotSource> slotSources(ConversationSessionRecord record) {
        return record.slotSources().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> new ConversationSlotSource(
                        ConversationSlotSource.SlotEnum.fromValue(entry.getKey()),
                        toApiSource(entry.getValue().source()))
                        .updatedAt(entry.getValue().updatedAt()))
                .toList();
    }

    private com.cadentia.generated.model.SlotValueSource toApiSource(SlotValueSource source) {
        return com.cadentia.generated.model.SlotValueSource.fromValue(source.name().toLowerCase());
    }

    private GenerateSetlistSlots toSlots(ConversationSlotUpdateRequestSlotPatch patch, GenerateSetlistSlots baseline) {
        if (patch == null) {
            return baseline;
        }
        SetlistCounts counts = patch.getCounts();
        KeyPolicy keyPolicy = patch.getKeyPolicy();
        TempoPolicy tempoPolicy = patch.getTempoPolicy();
        return new GenerateSetlistSlots(
                patch.getVerseText() == null ? baseline.verseText() : patch.getVerseText(),
                patch.getScriptureReferences() == null || patch.getScriptureReferences().isEmpty() ? baseline.scriptureReferences() : patch.getScriptureReferences(),
                patch.getThemeHints() == null || patch.getThemeHints().isEmpty() ? baseline.themeHints() : patch.getThemeHints(),
                counts == null ? baseline.counts() : new Counts(counts.getPraise(), counts.getWorship()),
                keyPolicy == null ? baseline.keyPolicy() : new IntentKeyPolicy(keyPolicy.getPreferSameKey(), keyPolicy.getAllowRelativeMajorMinor(), keyPolicy.getMaxKeyCenters()),
                tempoPolicy == null ? baseline.tempoPolicy() : new IntentTempoPolicy(tempoPolicy.getMaxJumpBpm()),
                patch.getLanguage() == null ? baseline.language() : patch.getLanguage(),
                patch.getEnergyArc() == null ? baseline.energyArc() : patch.getEnergyArc().getValue(),
                patch.getExcludedSongs() == null || patch.getExcludedSongs().isEmpty() ? baseline.excludedSongs() : patch.getExcludedSongs(),
                patch.getServiceMoment() == null ? baseline.serviceMoment() : patch.getServiceMoment().getValue());
    }

    private GenerateSetlistSlots empty() {
        return new GenerateSetlistSlots("", List.of(), List.of(), new Counts(10, 5), new IntentKeyPolicy(true, true, 2), new IntentTempoPolicy(12), null, null, List.of(), null);
    }

}

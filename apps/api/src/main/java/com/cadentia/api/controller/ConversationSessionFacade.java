package com.cadentia.api.controller;

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
    private final Map<UUID, SessionRecord> sessions = new HashMap<>();

    @Autowired
    public ConversationSessionFacade(
            DefaultSessionMergeService mergeService,
            ValidatedSetlistRequestMapper mapper,
            @Value("${cadentia.conversation.session.inactivity-timeout:PT30M}") Duration inactivityTimeout,
            @Value("${cadentia.conversation.session.absolute-lifetime:PT4H}") Duration absoluteLifetime,
            ObjectProvider<IntentService> intentServiceProvider) {
        this.mergeService = mergeService;
        this.mapper = mapper;
        this.inactivityTimeout = inactivityTimeout;
        this.absoluteLifetime = absoluteLifetime;
        this.intentService = intentServiceProvider.getIfAvailable();
    }

    public ConversationSessionFacade(
            DefaultSessionMergeService mergeService,
            ValidatedSetlistRequestMapper mapper,
            Duration inactivityTimeout,
            Duration absoluteLifetime) {
        this(mergeService, mapper, inactivityTimeout, absoluteLifetime, (IntentService) null);
    }

    public ConversationSessionFacade(
            DefaultSessionMergeService mergeService,
            ValidatedSetlistRequestMapper mapper,
            Duration inactivityTimeout,
            Duration absoluteLifetime,
            IntentService intentService) {
        this.mergeService = mergeService;
        this.mapper = mapper;
        this.inactivityTimeout = inactivityTimeout;
        this.absoluteLifetime = absoluteLifetime;
        this.intentService = intentService;
    }

    public ConversationSessionStateResponse get(UUID sessionId) {
        SessionRecord record = currentRecord(sessionId, ConversationState.COLLECTING);
        return snapshot(sessionId, record, List.of("Session state emitted."));
    }

    public ConversationSessionStateResponse clarify(UUID sessionId, ConversationClarificationRequest request) {
        SessionRecord record = transition(
                currentRecord(sessionId, ConversationState.COLLECTING),
                ConversationState.CLARIFICATION_REQUIRED,
                revision(ConversationRevisionEvent.EventTypeEnum.CLARIFICATION_ANSWER,
                        SlotValueSource.USER_EDIT,
                        "Clarification requested."));
        sessions.put(sessionId, record);
        return snapshot(sessionId, record, List.of("Clarification requested."));
    }

    public ConversationSessionStateResponse confirm(UUID sessionId, ConversationConfirmRequest request) {
        SessionRecord baseline = currentRecord(sessionId, ConversationState.COLLECTING);
        if (baseline.state() != ConversationState.READY_TO_CONFIRM) {
            return snapshot(
                    sessionId,
                    baseline,
                    List.of("Session is not ready to confirm. Provide a valid request before recommendation."));
        }
        SessionRecord record = transition(
                baseline,
                ConversationState.CONFIRMED,
                revision(ConversationRevisionEvent.EventTypeEnum.CONFIRM,
                        SlotValueSource.USER_EDIT,
                        "Session confirmed."));
        sessions.put(sessionId, record);
        return snapshot(sessionId, record, List.of("Session confirmed."));
    }

    public ConversationSessionStateResponse cancel(UUID sessionId) {
        SessionRecord record = transition(
                currentRecord(sessionId, ConversationState.COLLECTING),
                ConversationState.CANCELLED,
                revision(ConversationRevisionEvent.EventTypeEnum.CANCEL,
                        SlotValueSource.USER_EDIT,
                        "Session cancelled."));
        sessions.put(sessionId, record);
        return snapshot(sessionId, record, List.of("Session cancelled."));
    }

    public ConversationSessionStateResponse update(UUID sessionId, ConversationSlotUpdateRequest request) {
        SessionRecord baselineRecord = currentRecord(sessionId, ConversationState.COLLECTING);
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
        SessionRecord updated = new SessionRecord(
                ConversationState.COLLECTING,
                merged.mergedSlots(),
                mergedSources(baselineRecord.slotSources(), merged, updatedAt),
                appendRevisions(baselineRecord.revisionHistory(), mergeRevisions(merged, updatedAt)),
                baselineRecord.createdAt(),
                updatedAt,
                null);
        sessions.put(sessionId, updated);
        return snapshot(sessionId, updated, List.of("Session slots updated via deterministic merge."));
    }

    public ConversationSessionStateResponse ingestFreeText(UUID sessionId, String text) {
        if (intentService == null) {
            return update(sessionId, new ConversationSlotUpdateRequest().source(com.cadentia.generated.model.SlotValueSource.FREE_TEXT));
        }
        IntentParseResult parseResult = intentService.parse(text);
        ValidatedIntent parsedIntent = parseResult.intent();
        if (parsedIntent.intentType() == IntentType.CLARIFY_REQUEST) {
            SessionRecord record = transition(
                    currentRecord(sessionId, ConversationState.COLLECTING),
                    ConversationState.CLARIFICATION_REQUIRED,
                    revision(ConversationRevisionEvent.EventTypeEnum.CLARIFICATION_ANSWER,
                            SlotValueSource.FREE_TEXT,
                            "Intent extraction requested clarification."));
            sessions.put(sessionId, record);
            return snapshot(sessionId, record, List.of("Intent extraction requested clarification before recommendation."));
        }
        if (parsedIntent.intentType() == IntentType.UNSUPPORTED_REQUEST) {
            SessionRecord record = transition(
                    currentRecord(sessionId, ConversationState.COLLECTING),
                    ConversationState.CANCELLED,
                    revision(ConversationRevisionEvent.EventTypeEnum.CANCEL,
                            SlotValueSource.FREE_TEXT,
                            "Intent extraction safely rejected the request."));
            sessions.put(sessionId, record);
            return snapshot(sessionId, record, List.of("Intent extraction safely rejected the request before recommendation."));
        }
        GenerateSetlistIntent intent = (GenerateSetlistIntent) parsedIntent;
        SessionRecord baselineRecord = currentRecord(sessionId, ConversationState.COLLECTING);
        SessionMergeResult merged = mergeService.merge(
                baselineRecord.slots(),
                new SessionSlotUpdate(intent.slots(), SlotValueSource.FREE_TEXT, true));
        OffsetDateTime updatedAt = OffsetDateTime.now();
        SessionRecord updated = new SessionRecord(
                ConversationState.READY_TO_CONFIRM,
                merged.mergedSlots(),
                mergedSources(baselineRecord.slotSources(), merged, updatedAt),
                appendRevisions(baselineRecord.revisionHistory(), mergeRevisions(merged, updatedAt)),
                baselineRecord.createdAt(),
                updatedAt,
                null);
        sessions.put(sessionId, updated);
        return snapshot(sessionId, updated, List.of("Free-text request was validated by the shared intent boundary."));
    }

    public ConversationRecoveryResponse recover(UUID sessionId) {
        SessionRecord prior = currentRecord(sessionId, ConversationState.COLLECTING);
        SessionRecord expired = transition(
                prior,
                ConversationState.EXPIRED,
                revision(ConversationRevisionEvent.EventTypeEnum.EXPIRE,
                        SlotValueSource.DEFAULT,
                        "Session expired before recovery."));
        sessions.put(sessionId, expired);

        OffsetDateTime restartedAt = OffsetDateTime.now();
        SessionRecord restarted = new SessionRecord(
                ConversationState.START,
                empty(),
                defaultSlotSources(restartedAt),
                appendRevisions(expired.revisionHistory(), revision(
                        ConversationRevisionEvent.EventTypeEnum.RECOVER,
                        SlotValueSource.USER_EDIT,
                        "Session recovered with a fresh request state.")),
                restartedAt,
                restartedAt,
                null);
        sessions.put(sessionId, restarted);

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

    private SessionRecord currentRecord(UUID sessionId, ConversationState defaultState) {
        SessionRecord record = sessions.computeIfAbsent(
                sessionId,
                id -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    return new SessionRecord(defaultState, empty(), defaultSlotSources(now), List.of(), now, now, null);
                });
        if (record.state() == ConversationState.EXPIRED) {
            return record;
        }
        if (isExpired(record)) {
            SessionRecord expired = transition(
                    record,
                    ConversationState.EXPIRED,
                    revision(ConversationRevisionEvent.EventTypeEnum.EXPIRE,
                            SlotValueSource.DEFAULT,
                            "Session expired."));
            sessions.put(sessionId, expired);
            return expired;
        }
        return record;
    }

    private boolean isExpired(SessionRecord record) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean inactive = record.updatedAt().plus(inactivityTimeout).isBefore(now);
        boolean tooOld = record.createdAt().plus(absoluteLifetime).isBefore(now);
        return inactive || tooOld;
    }

    private SessionRecord transition(
            SessionRecord record,
            ConversationState targetState,
            ConversationRevisionEvent revision) {
        OffsetDateTime updatedAt = OffsetDateTime.now();
        return new SessionRecord(
                targetState,
                record.slots(),
                record.slotSources(),
                appendRevisions(record.revisionHistory(), revision),
                record.createdAt(),
                updatedAt,
                targetState == ConversationState.CONFIRMED ? updatedAt : record.confirmedAt());
    }

    private ConversationSessionStateResponse snapshot(UUID sessionId, SessionRecord record, List<String> messages) {
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
                .confirmedAt(record.confirmedAt());
    }

    private Map<String, SourceStamp> mergedSources(
            Map<String, SourceStamp> baselineSources,
            SessionMergeResult merged,
            OffsetDateTime updatedAt) {
        Map<String, SourceStamp> sources = new HashMap<>(baselineSources);
        merged.slotSources().forEach((slot, source) -> sources.put(slot, new SourceStamp(source, updatedAt)));
        return Map.copyOf(sources);
    }

    private Map<String, SourceStamp> defaultSlotSources(OffsetDateTime updatedAt) {
        return Map.of(
                "counts", new SourceStamp(SlotValueSource.DEFAULT, updatedAt),
                "keyPolicy", new SourceStamp(SlotValueSource.DEFAULT, updatedAt),
                "tempoPolicy", new SourceStamp(SlotValueSource.DEFAULT, updatedAt));
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

    private List<ConversationSlotSource> slotSources(SessionRecord record) {
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

    private record SessionRecord(
            ConversationState state,
            GenerateSetlistSlots slots,
            Map<String, SourceStamp> slotSources,
            List<ConversationRevisionEvent> revisionHistory,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime confirmedAt) {}

    private record SourceStamp(SlotValueSource source, OffsetDateTime updatedAt) {}
}

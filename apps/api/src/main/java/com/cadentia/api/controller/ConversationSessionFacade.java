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
import com.cadentia.intent.DefaultSessionMergeService;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.SessionMergeResult;
import com.cadentia.intent.SessionSlotUpdate;
import com.cadentia.intent.SlotValueSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConversationSessionFacade {
    private final DefaultSessionMergeService mergeService;
    private final ValidatedSetlistRequestMapper mapper;
    private final Map<UUID, GenerateSetlistSlots> sessions = new HashMap<>();

    public ConversationSessionFacade(DefaultSessionMergeService mergeService, ValidatedSetlistRequestMapper mapper) {
        this.mergeService = mergeService;
        this.mapper = mapper;
    }

    ConversationSessionStateResponse get(UUID sessionId) {
        return snapshot(sessionId, ConversationState.COLLECTING);
    }

    ConversationSessionStateResponse clarify(UUID sessionId, ConversationClarificationRequest request) {
        return snapshot(sessionId, ConversationState.CLARIFICATION_REQUIRED);
    }

    ConversationSessionStateResponse confirm(UUID sessionId, ConversationConfirmRequest request) {
        return snapshot(sessionId, ConversationState.CONFIRMED);
    }

    ConversationSessionStateResponse cancel(UUID sessionId) {
        return snapshot(sessionId, ConversationState.CANCELLED);
    }

    ConversationSessionStateResponse update(UUID sessionId, ConversationSlotUpdateRequest request) {
        GenerateSetlistSlots baseline = sessions.computeIfAbsent(sessionId, id -> empty());
        SessionMergeResult merged = mergeService.merge(
                baseline,
                new SessionSlotUpdate(
                        empty(),
                        SlotValueSource.valueOf(request.getSource().getValue().toUpperCase()),
                        false));
        sessions.put(sessionId, merged.mergedSlots());
        return snapshot(sessionId, ConversationState.COLLECTING);
    }

    ConversationRecoveryResponse recover(UUID sessionId) {
        ConversationSessionStateResponse recovered = snapshot(sessionId, ConversationState.START);
        return new ConversationRecoveryResponse(sessionId, ConversationState.EXPIRED, recovered, "Session expired and has been restarted.", true);
    }

    private ConversationSessionStateResponse snapshot(UUID sessionId, ConversationState state) {
        GenerateSetlistRequest slots = mapper.toGenerateSetlistRequest(new com.cadentia.intent.GenerateSetlistIntent(
                "v1", sessions.computeIfAbsent(sessionId, id -> empty())));
        return new ConversationSessionStateResponse(
                sessionId,
                state,
                ChannelType.MIXED,
                slots,
                new ArrayList<ConversationSlotSource>(),
                new ArrayList<ConversationRevisionEvent>(),
                OffsetDateTime.now().plusMinutes(30),
                List.of("Session state emitted."));
    }

    private GenerateSetlistSlots empty() {
        return new GenerateSetlistSlots("", List.of(), List.of(), null, null, null, null, null, List.of(), null);
    }
}

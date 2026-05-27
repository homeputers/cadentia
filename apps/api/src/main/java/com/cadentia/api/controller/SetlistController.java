package com.cadentia.api.controller;

import com.cadentia.generated.api.SetlistsApi;
import com.cadentia.generated.model.GenerateSetlistRequest;

import com.cadentia.generated.model.ConversationClarificationRequest;
import com.cadentia.generated.model.ConversationConfirmRequest;
import com.cadentia.generated.model.ConversationRecoveryResponse;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.ConversationSlotUpdateRequest;
import java.util.UUID;
import com.cadentia.generated.model.NaturalLanguageSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.intent.ClarifyRequestIntent;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.IntentType;
import com.cadentia.intent.UnsupportedRequestIntent;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentService;
import com.cadentia.reng.SetlistService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SetlistController implements SetlistsApi {

    private final SetlistService setlistService;
    private final IntentService intentService;
    private final ValidatedSetlistRequestMapper requestMapper;
    private final ConversationSessionFacade conversationSessionFacade;

    public SetlistController(
            SetlistService setlistService,
            IntentService intentService,
            ValidatedSetlistRequestMapper requestMapper) {
        this(setlistService, intentService, requestMapper, new ConversationSessionFacade(new com.cadentia.intent.DefaultSessionMergeService(), requestMapper));
    }

    public SetlistController(
            SetlistService setlistService,
            IntentService intentService,
            ValidatedSetlistRequestMapper requestMapper,
            ConversationSessionFacade conversationSessionFacade) {
        this.setlistService = setlistService;
        this.intentService = intentService;
        this.requestMapper = requestMapper;
        this.conversationSessionFacade = conversationSessionFacade;
    }

    @Override
    public ResponseEntity<SetlistProposalResponse> generateSetlistProposal(GenerateSetlistRequest request) {
        return ResponseEntity.accepted().body(setlistService.generate(request));
    }

    @Override
    public ResponseEntity<SetlistProposalResponse> generateSetlistProposalFromNaturalLanguage(
            NaturalLanguageSetlistRequest request) {
        IntentParseResult parseResult = intentService.parse(request.getText());
        if (parseResult.intent().intentType() != IntentType.GENERATE_SETLIST) {
            return ResponseEntity.accepted().body(safeIntentResponse(parseResult));
        }

        GenerateSetlistIntent intent = (GenerateSetlistIntent) parseResult.intent();
        GenerateSetlistRequest validatedRequest = requestMapper.toGenerateSetlistRequest(intent);
        return ResponseEntity.accepted().body(setlistService.generate(validatedRequest));
    }

    @Override
    public ResponseEntity<ConversationSessionStateResponse> getConversationSessionState(UUID sessionId) {
        return ResponseEntity.ok(conversationSessionFacade.get(sessionId));
    }

    @Override
    public ResponseEntity<ConversationSessionStateResponse> updateConversationSessionSlots(
            UUID sessionId, ConversationSlotUpdateRequest conversationSlotUpdateRequest) {
        return ResponseEntity.ok(conversationSessionFacade.update(sessionId, conversationSlotUpdateRequest));
    }

    @Override
    public ResponseEntity<ConversationSessionStateResponse> requestConversationClarification(
            UUID sessionId, ConversationClarificationRequest conversationClarificationRequest) {
        return ResponseEntity.ok(conversationSessionFacade.clarify(sessionId, conversationClarificationRequest));
    }

    @Override
    public ResponseEntity<ConversationSessionStateResponse> confirmConversationSession(
            UUID sessionId, ConversationConfirmRequest conversationConfirmRequest) {
        return ResponseEntity.ok(conversationSessionFacade.confirm(sessionId, conversationConfirmRequest));
    }

    @Override
    public ResponseEntity<ConversationSessionStateResponse> cancelConversationSession(UUID sessionId) {
        return ResponseEntity.ok(conversationSessionFacade.cancel(sessionId));
    }

    @Override
    public ResponseEntity<ConversationRecoveryResponse> recoverConversationSession(UUID sessionId) {
        return ResponseEntity.ok(conversationSessionFacade.recover(sessionId));
    }

    private SetlistProposalResponse safeIntentResponse(IntentParseResult parseResult) {
        return switch (parseResult.intent().intentType()) {
            case CLARIFY_REQUEST -> clarifyResponse((ClarifyRequestIntent) parseResult.intent());
            case UNSUPPORTED_REQUEST -> unsupportedResponse((UnsupportedRequestIntent) parseResult.intent());
            case GENERATE_SETLIST -> throw new IllegalArgumentException("Generate intents should invoke recommendation.");
        };
    }

    private SetlistProposalResponse clarifyResponse(ClarifyRequestIntent intent) {
        List<String> auditMessages = new ArrayList<>();
        auditMessages.add("Intent extraction requested clarification before recommendation.");
        auditMessages.add(intent.clarificationQuestion());
        if (!intent.missingSlots().isEmpty()) {
            auditMessages.add("Missing slots: " + String.join(", ", intent.missingSlots()) + ".");
        }
        return new SetlistProposalResponse()
                .status("CLARIFICATION_REQUIRED")
                .auditMessages(auditMessages);
    }

    private SetlistProposalResponse unsupportedResponse(UnsupportedRequestIntent intent) {
        return new SetlistProposalResponse()
                .status("UNSUPPORTED_REQUEST")
                .auditMessages(List.of(
                        "Intent extraction rejected the request before recommendation.",
                        intent.safeMessage()));
    }
}

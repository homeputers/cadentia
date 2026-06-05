package com.cadentia.api.controller;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.api.SetlistsApi;
import com.cadentia.generated.model.CommitSetlistEditsRequest;
import com.cadentia.generated.model.ConversationClarificationRequest;
import com.cadentia.generated.model.ConversationConfirmRequest;
import com.cadentia.generated.model.ConversationRecoveryResponse;
import com.cadentia.generated.model.ConversationSessionStateResponse;
import com.cadentia.generated.model.ConversationSlotUpdateRequest;
import com.cadentia.generated.model.CreateSetlistBaselineRequest;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.NaturalLanguageSetlistRequest;
import com.cadentia.generated.model.SetlistDiffOperation;
import com.cadentia.generated.model.SetlistItemChangeType;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.SetlistProvenanceType;
import com.cadentia.generated.model.SetlistVersion;
import com.cadentia.generated.model.SetlistVersionDiffResponse;
import com.cadentia.generated.model.SetlistVersionEnvelope;
import com.cadentia.generated.model.SetlistVersionItem;
import com.cadentia.generated.model.SetlistVersionListResponse;
import com.cadentia.generated.model.SetlistVersionStatus;
import com.cadentia.generated.model.SetlistVersionSummary;
import com.cadentia.intent.ClarifyRequestIntent;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.IntentType;
import com.cadentia.intent.UnsupportedRequestIntent;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentService;
import com.cadentia.reng.SetlistService;
import com.cadentia.reng.setlist.SetlistVersionDiffService;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;
import com.cadentia.reng.setlist.SetlistVersionService;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SetlistController implements SetlistsApi {

    private final SetlistService setlistService;
    private final IntentService intentService;
    private final ValidatedSetlistRequestMapper requestMapper;
    private final ConversationSessionFacade conversationSessionFacade;
    private final SetlistVersionService setlistVersionService;
    private final SetlistVersionDiffService setlistVersionDiffService;

    public SetlistController(
            SetlistService setlistService,
            IntentService intentService,
            ValidatedSetlistRequestMapper requestMapper,
            ConversationSessionFacade conversationSessionFacade,
            SetlistVersionService setlistVersionService,
            SetlistVersionDiffService setlistVersionDiffService) {
        this.setlistService = setlistService;
        this.intentService = intentService;
        this.requestMapper = requestMapper;
        this.conversationSessionFacade = conversationSessionFacade;
        this.setlistVersionService = setlistVersionService;
        this.setlistVersionDiffService = setlistVersionDiffService;
    }


    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<SetlistProposalResponse> generateSetlistProposal(GenerateSetlistRequest request) {
        if (!authorizeExplanationAudience(request)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.accepted().body(setlistService.generate(request));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<SetlistProposalResponse> generateSetlistProposalFromNaturalLanguage(
            NaturalLanguageSetlistRequest request) {
        IntentParseResult parseResult = intentService.parse(request.getText());
        if (parseResult.intent().intentType() != IntentType.GENERATE_SETLIST) {
            return ResponseEntity.accepted().body(safeIntentResponse(parseResult));
        }

        GenerateSetlistIntent intent = (GenerateSetlistIntent) parseResult.intent();
        GenerateSetlistRequest validatedRequest = requestMapper.toGenerateSetlistRequest(intent);
        if (!authorizeExplanationAudience(validatedRequest)) {
            return ResponseEntity.status(403).build();
        }
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

    @Override
    public ResponseEntity<SetlistVersionListResponse> listSetlistVersions(UUID setlistId) {
        if (setlistVersionService == null) {
            return ResponseEntity.status(501).build();
        }
        List<SetlistVersionSummary> versions = setlistVersionService.listVersions(setlistId).stream()
                .map(v -> new SetlistVersionSummary(v.versionId(), v.versionNumber(), SetlistProvenanceType.fromValue(v.provenanceType()), SetlistVersionStatus.DRAFT)
                        .parentVersionId(v.parentVersionId())
                        .createdAt(v.createdAt().atOffset(ZoneOffset.UTC)))
                .toList();
        return ResponseEntity.ok(new SetlistVersionListResponse(setlistId, versions));
    }

    @Override
    public ResponseEntity<SetlistVersionEnvelope> getSetlistVersion(UUID setlistId, UUID versionId) {
        if (setlistVersionService == null) {
            return ResponseEntity.status(501).build();
        }
        return setlistVersionService.findVersion(setlistId, versionId)
                .map(v -> ResponseEntity.ok(new SetlistVersionEnvelope(setlistId, toApiVersion(v))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<SetlistVersionDiffResponse> diffSetlistVersions(UUID setlistId, UUID fromVersionId, UUID toVersionId) {
        if (setlistVersionService == null || setlistVersionDiffService == null) {
            return ResponseEntity.status(501).build();
        }
        Optional<SetlistVersionSnapshot> from = setlistVersionService.findVersion(setlistId, fromVersionId);
        Optional<SetlistVersionSnapshot> to = setlistVersionService.findVersion(setlistId, toVersionId);
        if (from.isEmpty() || to.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(setlistVersionDiffService.diff(from.get(), to.get()));
    }

    @Override
    public ResponseEntity<SetlistVersionEnvelope> createSetlistBaselineVersion(CreateSetlistBaselineRequest createSetlistBaselineRequest) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<SetlistVersionEnvelope> commitSetlistEdits(UUID setlistId, CommitSetlistEditsRequest commitSetlistEditsRequest) {
        return ResponseEntity.status(501).build();
    }

    private boolean authorizeExplanationAudience(GenerateSetlistRequest request) {
        if (request == null) {
            return true;
        }
        GenerateSetlistRequest.ExplanationAudienceEnum requestedAudience = request.getExplanationAudience();
        boolean adminDiagnosticsRequested = Boolean.TRUE.equals(request.getIncludeAdminDiagnostics())
                || requestedAudience == GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN;
        if (!adminDiagnosticsRequested) {
            if (request.getIncludeAdminDiagnostics() == null) {
                request.setIncludeAdminDiagnostics(false);
            }
            return true;
        }
        if (!currentUserHasAuthority(RbacAuthorities.ROLE_ADMIN)) {
            request.setIncludeAdminDiagnostics(false);
            if (requestedAudience == GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN) {
                request.setExplanationAudience(GenerateSetlistRequest.ExplanationAudienceEnum.PUBLIC);
            }
            return false;
        }
        request.setExplanationAudience(GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN);
        request.setIncludeAdminDiagnostics(true);
        return true;
    }

    private boolean currentUserHasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private SetlistVersion toApiVersion(SetlistVersionSnapshot snapshot) {
        List<SetlistVersionItem> items = snapshot.items().stream()
                .map(item -> new SetlistVersionItem(item.id(), item.positionIndex() + 1, item.catalogArrangementId(),
                        SetlistProvenanceType.fromValue(item.itemProvenance())).transposeSemitones(item.transposedKey() == null ? 0 : 1))
                .toList();
        return new SetlistVersion(snapshot.versionId(), snapshot.versionNumber(),
                SetlistProvenanceType.fromValue(snapshot.provenanceType()), SetlistVersionStatus.DRAFT,
                snapshot.engineVersion(), items)
                .parentVersionId(snapshot.parentVersionId())
                .createdAt(snapshot.createdAt().atOffset(ZoneOffset.UTC))
                .parsedIntent(Collections.emptyMap())
                .explanationFacts(List.of())
                .readinessSummary(snapshot.readinessSummary() == null
                        ? null
                        : snapshot.readinessSummary().toReadinessSummary());
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

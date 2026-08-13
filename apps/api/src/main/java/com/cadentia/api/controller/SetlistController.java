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
import com.cadentia.generated.model.SetlistEditOperation;
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
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistItemCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistEditEventCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionItemSnapshot;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;
import com.cadentia.reng.setlist.SetlistVersionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SetlistController implements SetlistsApi {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String GENERATED_ITEM_PROVENANCE = "GENERATED";
    private static final String MANUAL_ITEM_PROVENANCE = "MANUAL";

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
        if (setlistVersionService == null) {
            return ResponseEntity.status(501).build();
        }
        if (createSetlistBaselineRequest == null
                || createSetlistBaselineRequest.getItems() == null
                || createSetlistBaselineRequest.getItems().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            SetlistVersionSnapshot snapshot = setlistVersionService.createBaseline(new CreateSetlistBaselineCommand(
                    currentActor("setlist-api"),
                    createSetlistBaselineRequest.getEngineProfileVersion(),
                    createSetlistBaselineRequest.getEngineProfileVersion(),
                    json(createSetlistBaselineRequest.getRequest()),
                    json(createSetlistBaselineRequest.getParsedIntent()),
                    json(createSetlistBaselineRequest.getExplanationFacts() == null
                            ? List.of()
                            : createSetlistBaselineRequest.getExplanationFacts()),
                    createSetlistBaselineRequest.getItems().stream()
                            .sorted(Comparator.comparing(item -> requiredPosition(item.getPosition())))
                            .map(this::baselineItem)
                            .toList(),
                    "LINEAR"));
            return ResponseEntity.status(201).body(new SetlistVersionEnvelope(snapshot.setlistId(), toApiVersion(snapshot)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Override
    public ResponseEntity<SetlistVersionEnvelope> commitSetlistEdits(UUID setlistId, CommitSetlistEditsRequest commitSetlistEditsRequest) {
        if (setlistVersionService == null) {
            return ResponseEntity.status(501).build();
        }
        if (setlistId == null
                || commitSetlistEditsRequest == null
                || commitSetlistEditsRequest.getBaseVersionId() == null
                || commitSetlistEditsRequest.getOperations() == null
                || commitSetlistEditsRequest.getOperations().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<SetlistVersionSnapshot> base = setlistVersionService.findVersion(setlistId, commitSetlistEditsRequest.getBaseVersionId());
        if (base.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!isLatestVersion(setlistId, base.get())) {
            return ResponseEntity.status(409).build();
        }
        try {
            List<EditableSetlistItem> editedItems = applyEdits(base.get().items(), commitSetlistEditsRequest.getOperations());
            SetlistVersionSnapshot snapshot = setlistVersionService.createEdit(new CreateSetlistVersionCommand(
                    setlistId,
                    base.get().versionId(),
                    currentActor(commitSetlistEditsRequest.getActorId()),
                    base.get().scoringProfileVersion(),
                    base.get().engineVersion(),
                    "{}",
                    "{}",
                    "[]",
                    "Applied " + commitSetlistEditsRequest.getOperations().size() + " setlist edit operation(s).",
                    editedItems.stream().map(EditableSetlistItem::toCommand).toList(),
                    editEvents(commitSetlistEditsRequest.getOperations())));
            return ResponseEntity.status(201).body(new SetlistVersionEnvelope(setlistId, toApiVersion(snapshot)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
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

    private String currentActor(String fallback) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return fallback == null || fallback.isBlank() ? "setlist-api" : fallback;
        }
        return authentication.getName();
    }

    private SetlistVersion toApiVersion(SetlistVersionSnapshot snapshot) {
        List<SetlistVersionItem> items = snapshot.items().stream()
                .map(item -> new SetlistVersionItem(item.id(), item.positionIndex() + 1, item.catalogArrangementId(),
                        apiItemProvenance(item.itemProvenance())).transposeSemitones(transposeSemitones(item.transposedKey())))
                .toList();
        return new SetlistVersion(snapshot.versionId(), snapshot.versionNumber(),
                SetlistProvenanceType.fromValue(snapshot.provenanceType()), SetlistVersionStatus.DRAFT,
                snapshot.scoringProfileVersion(), items)
                .parentVersionId(snapshot.parentVersionId())
                .createdAt(snapshot.createdAt().atOffset(ZoneOffset.UTC))
                .parsedIntent(Collections.emptyMap())
                .explanationFacts(List.of())
                .readinessSummary(snapshot.readinessSummary() == null
                        ? null
                        : snapshot.readinessSummary().toReadinessSummary());
    }

    private CreateSetlistItemCommand baselineItem(SetlistVersionItem item) {
        return new CreateSetlistItemCommand(
                item.getPosition() - 1,
                item.getCatalogArrangementId(),
                transposedKey(item.getTransposeSemitones()),
                null,
                null,
                storageItemProvenance(item.getProvenance()),
                null);
    }

    private boolean isLatestVersion(UUID setlistId, SetlistVersionSnapshot base) {
        return setlistVersionService.listVersions(setlistId).stream()
                .mapToInt(SetlistVersionSnapshot::versionNumber)
                .max()
                .orElse(base.versionNumber()) == base.versionNumber();
    }

    private List<EditableSetlistItem> applyEdits(
            List<SetlistVersionItemSnapshot> baseItems,
            List<SetlistEditOperation> operations) {
        List<EditableSetlistItem> items = baseItems.stream()
                .sorted(Comparator.comparing(SetlistVersionItemSnapshot::positionIndex))
                .map(EditableSetlistItem::fromSnapshot)
                .collect(Collectors.toCollection(ArrayList::new));
        for (SetlistEditOperation operation : operations) {
            if (operation == null || operation.getAction() == null) {
                throw new IllegalArgumentException("Setlist edit action is required.");
            }
            switch (operation.getAction()) {
                case REORDER -> reorder(items, operation);
                case REPLACE -> replace(items, operation);
                case REMOVE -> items.remove(resolveItem(items, operation));
                case TRANSPOSE -> transpose(items, operation);
                default -> throw new IllegalArgumentException("Unsupported setlist edit operation.");
            }
            reindex(items);
        }
        return List.copyOf(items);
    }

    private void reorder(List<EditableSetlistItem> items, SetlistEditOperation operation) {
        EditableSetlistItem item = resolveItem(items, operation);
        int toIndex = requiredPosition(operation.getToPosition()) - 1;
        if (toIndex > items.size() - 1) {
            throw new IllegalArgumentException("Reorder target position is outside the setlist.");
        }
        items.remove(item);
        items.add(toIndex, item.withManualProvenance());
    }

    private void replace(List<EditableSetlistItem> items, SetlistEditOperation operation) {
        if (operation.getReplacementArrangementId() == null) {
            throw new IllegalArgumentException("Replacement arrangement is required.");
        }
        EditableSetlistItem item = resolveItem(items, operation);
        items.set(items.indexOf(item), item.withArrangement(operation.getReplacementArrangementId()));
    }

    private void transpose(List<EditableSetlistItem> items, SetlistEditOperation operation) {
        EditableSetlistItem item = resolveItem(items, operation);
        int semitones = operation.getSemitoneDelta() == null ? 0 : operation.getSemitoneDelta();
        items.set(items.indexOf(item), item.withTranspose(semitones));
    }

    private EditableSetlistItem resolveItem(List<EditableSetlistItem> items, SetlistEditOperation operation) {
        if (operation.getItemId() != null) {
            return items.stream()
                    .filter(item -> item.sourceItemId().equals(operation.getItemId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Setlist item was not found."));
        }
        int position = requiredPosition(operation.getFromPosition());
        if (position > items.size()) {
            throw new IllegalArgumentException("Setlist position was not found.");
        }
        return items.get(position - 1);
    }

    private int requiredPosition(Integer position) {
        if (position == null || position < 1) {
            throw new IllegalArgumentException("A valid 1-based position is required.");
        }
        return position;
    }

    private void reindex(List<EditableSetlistItem> items) {
        for (int index = 0; index < items.size(); index++) {
            items.set(index, items.get(index).withPositionIndex(index));
        }
    }

    private List<SetlistEditEventCommand> editEvents(List<SetlistEditOperation> operations) {
        List<SetlistEditEventCommand> events = new ArrayList<>();
        for (int index = 0; index < operations.size(); index++) {
            SetlistEditOperation operation = operations.get(index);
            events.add(new SetlistEditEventCommand(
                    index,
                    operation.getAction().getValue(),
                    operation.getItemId(),
                    zeroBased(operation.getFromPosition()),
                    zeroBased(operation.getToPosition()),
                    operation.getReplacementArrangementId(),
                    transposedKey(operation.getSemitoneDelta()),
                    null,
                    operation.getAction() == SetlistEditOperation.ActionEnum.REMOVE,
                    json(Map.of(
                            "action", operation.getAction().getValue(),
                            "semitoneDelta", operation.getSemitoneDelta() == null ? 0 : operation.getSemitoneDelta()))));
        }
        return events;
    }

    private Integer zeroBased(Integer position) {
        return position == null ? null : position - 1;
    }

    private SetlistProvenanceType apiItemProvenance(String itemProvenance) {
        return MANUAL_ITEM_PROVENANCE.equals(itemProvenance)
                ? SetlistProvenanceType.MANUAL_EDIT
                : SetlistProvenanceType.GENERATED_BASELINE;
    }

    private String storageItemProvenance(SetlistProvenanceType provenance) {
        return provenance == SetlistProvenanceType.MANUAL_EDIT
                ? MANUAL_ITEM_PROVENANCE
                : GENERATED_ITEM_PROVENANCE;
    }

    private String transposedKey(Integer semitones) {
        if (semitones == null || semitones == 0) {
            return null;
        }
        return "semitone:" + (semitones > 0 ? "+" : "") + semitones;
    }

    private int transposeSemitones(String transposedKey) {
        if (transposedKey == null || transposedKey.isBlank()) {
            return 0;
        }
        if (transposedKey.startsWith("semitone:")) {
            try {
                return Integer.parseInt(transposedKey.substring("semitone:".length()));
            } catch (NumberFormatException ex) {
                return 1;
            }
        }
        return 1;
    }

    private String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Payload could not be serialized.", ex);
        }
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

    private record EditableSetlistItem(
            int positionIndex,
            UUID catalogArrangementId,
            String transposedKey,
            String transposedMode,
            UUID sourceItemId,
            String itemProvenance,
            String notes) {

        private static EditableSetlistItem fromSnapshot(SetlistVersionItemSnapshot item) {
            return new EditableSetlistItem(
                    item.positionIndex(),
                    item.catalogArrangementId(),
                    item.transposedKey(),
                    item.transposedMode(),
                    item.id(),
                    item.itemProvenance(),
                    item.notes());
        }

        private EditableSetlistItem withPositionIndex(int positionIndex) {
            return new EditableSetlistItem(
                    positionIndex,
                    catalogArrangementId,
                    transposedKey,
                    transposedMode,
                    sourceItemId,
                    itemProvenance,
                    notes);
        }

        private EditableSetlistItem withManualProvenance() {
            return new EditableSetlistItem(
                    positionIndex,
                    catalogArrangementId,
                    transposedKey,
                    transposedMode,
                    sourceItemId,
                    MANUAL_ITEM_PROVENANCE,
                    notes);
        }

        private EditableSetlistItem withArrangement(UUID arrangementId) {
            return new EditableSetlistItem(
                    positionIndex,
                    arrangementId,
                    transposedKey,
                    transposedMode,
                    sourceItemId,
                    MANUAL_ITEM_PROVENANCE,
                    notes);
        }

        private EditableSetlistItem withTranspose(int semitones) {
            return new EditableSetlistItem(
                    positionIndex,
                    catalogArrangementId,
                    semitones == 0 ? null : "semitone:" + (semitones > 0 ? "+" : "") + semitones,
                    null,
                    sourceItemId,
                    MANUAL_ITEM_PROVENANCE,
                    notes);
        }

        private CreateSetlistItemCommand toCommand() {
            return new CreateSetlistItemCommand(
                    positionIndex,
                    catalogArrangementId,
                    transposedKey,
                    transposedMode,
                    sourceItemId,
                    itemProvenance,
                    notes);
        }
    }
}

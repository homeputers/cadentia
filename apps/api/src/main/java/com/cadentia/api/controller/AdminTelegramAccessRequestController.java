package com.cadentia.api.controller;

import com.cadentia.bot.telegram.TelegramAccessRequest;
import com.cadentia.bot.telegram.TelegramAccessRequestDecisionException;
import com.cadentia.bot.telegram.TelegramAccessRequestService;
import com.cadentia.bot.telegram.TelegramAccessRequestStatus;
import com.cadentia.generated.api.AdminTelegramAccessApi;
import com.cadentia.generated.model.TelegramAccessRequestDecisionRequest;
import com.cadentia.generated.model.TelegramAccessRequestDecisionResponse;
import com.cadentia.generated.model.TelegramAccessRequestListResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminTelegramAccessRequestController implements AdminTelegramAccessApi {

    private final TelegramAccessRequestService accessRequestService;

    public AdminTelegramAccessRequestController(TelegramAccessRequestService accessRequestService) {
        this.accessRequestService = accessRequestService;
    }

    @Override
    public ResponseEntity<TelegramAccessRequestListResponse> listTelegramAccessRequests(String xChurchInstanceId, String status) {
        TelegramAccessRequestStatus filter = parseStatus(status);
        List<com.cadentia.generated.model.TelegramAccessRequest> items = accessRequestService
                .listRequests(xChurchInstanceId, filter)
                .stream()
                .map(AdminTelegramAccessRequestController::toModel)
                .toList();
        return ResponseEntity.ok(new TelegramAccessRequestListResponse()
                .churchInstanceId(xChurchInstanceId)
                .items(items));
    }

    @Override
    public ResponseEntity<TelegramAccessRequestDecisionResponse> approveTelegramAccessRequest(
            String xChurchInstanceId, UUID requestId, TelegramAccessRequestDecisionRequest request) {
        TelegramAccessRequest decided = decide(requestId, request, true);
        return ResponseEntity.ok(new TelegramAccessRequestDecisionResponse().request(toModel(decided)));
    }

    @Override
    public ResponseEntity<TelegramAccessRequestDecisionResponse> rejectTelegramAccessRequest(
            String xChurchInstanceId, UUID requestId, TelegramAccessRequestDecisionRequest request) {
        TelegramAccessRequest decided = decide(requestId, request, false);
        return ResponseEntity.ok(new TelegramAccessRequestDecisionResponse().request(toModel(decided)));
    }

    private TelegramAccessRequest decide(UUID requestId, TelegramAccessRequestDecisionRequest request, boolean approve) {
        String reason = request == null ? null : request.getReason();
        String adminActorId = currentActorId();
        try {
            return approve
                    ? accessRequestService.approve(requestId, adminActorId, reason)
                    : accessRequestService.reject(requestId, adminActorId, reason);
        } catch (TelegramAccessRequestDecisionException ex) {
            HttpStatus status = ex.reasonKind() == TelegramAccessRequestDecisionException.Reason.NOT_FOUND
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.CONFLICT;
            throw new ResponseStatusException(status, ex.getMessage());
        }
    }

    private String currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }

    private TelegramAccessRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return TelegramAccessRequestStatus.PENDING;
        }
        try {
            return TelegramAccessRequestStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid access request status filter.");
        }
    }

    private static com.cadentia.generated.model.TelegramAccessRequest toModel(TelegramAccessRequest request) {
        com.cadentia.generated.model.TelegramAccessRequest model = new com.cadentia.generated.model.TelegramAccessRequest()
                .requestId(request.id())
                .churchInstanceId(request.churchInstanceId())
                .status(com.cadentia.generated.model.TelegramAccessRequest.StatusEnum.fromValue(request.status().name()))
                .requestedAt(OffsetDateTime.ofInstant(request.requestedAt(), ZoneOffset.UTC))
                .maskedReference(request.maskedReference())
                .decidedBy(request.decidedBy())
                .decisionReason(request.decisionReason());
        if (request.decidedAt() != null) {
            model.decidedAt(OffsetDateTime.ofInstant(request.decidedAt(), ZoneOffset.UTC));
        }
        return model;
    }
}

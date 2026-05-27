package com.cadentia.api.controller;

import com.cadentia.feedback.FeedbackModels.FeedbackEventRecord;
import com.cadentia.feedback.FeedbackModels.FeedbackResetResult;
import com.cadentia.feedback.FeedbackModels.FeedbackScopeAggregate;
import com.cadentia.feedback.FeedbackService;
import com.cadentia.generated.api.FeedbackTuningApi;
import com.cadentia.generated.model.CreateFeedbackEventRequest;
import com.cadentia.generated.model.FeedbackEventResponse;
import com.cadentia.generated.model.FeedbackResetRequest;
import com.cadentia.generated.model.FeedbackResetResponse;
import com.cadentia.generated.model.FeedbackScopeLayer;
import com.cadentia.generated.model.FeedbackScopeStateResponse;
import com.cadentia.generated.model.FeedbackScopeStateResponseCounters;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedbackTuningController implements FeedbackTuningApi {
    private final FeedbackService feedbackService;

    public FeedbackTuningController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FeedbackEventResponse> recordFeedbackEvent(CreateFeedbackEventRequest request) {
        FeedbackEventRecord saved = feedbackService.createEvent(new FeedbackEventRecord(
                null,
                request.getSetlistId(),
                request.getSetlistVersionId(),
                request.getArrangementId(),
                request.getOutcome().getValue(),
                request.getScopeLayer().getValue(),
                request.getScopeId(),
                request.getActorId(),
                request.getReplacementReason() == null ? null : request.getReplacementReason().getValue(),
                request.getReplacedWithArrangementId(),
                request.getFamiliarityScore(),
                null));
        return ResponseEntity.status(201).body(toResponse(saved));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FeedbackEventResponse>> listFeedbackEvents(FeedbackScopeLayer scopeLayer, UUID scopeId, UUID arrangementId) {
        return ResponseEntity.ok(feedbackService.listEvents(
                scopeLayer == null ? null : scopeLayer.getValue(), scopeId, arrangementId).stream().map(this::toResponse).toList());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FeedbackScopeStateResponse> getFeedbackScopeState(FeedbackScopeLayer scopeLayer, UUID scopeId) {
        FeedbackScopeAggregate aggregate = feedbackService.getScopeStateWithFallback(scopeLayer.getValue(), scopeId);
        return ResponseEntity.ok(new FeedbackScopeStateResponse()
                .scopeLayer(FeedbackScopeLayer.fromValue(aggregate.scopeLayer()))
                .scopeId(aggregate.scopeId())
                .counters(new FeedbackScopeStateResponseCounters()
                        .accepted((long) aggregate.acceptedCount())
                        .rejected((long) aggregate.rejectedCount())
                        .skipped((long) aggregate.skippedCount())
                        .favorited((long) aggregate.favoritedCount()))
                .replacementReasonCounts(toLongMap(aggregate.replacementReasonCounts()))
                .lastFeedbackAt(aggregate.lastFeedbackAt() == null ? null : OffsetDateTime.ofInstant(aggregate.lastFeedbackAt(), ZoneOffset.UTC)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('catalog.admin.review','catalog.admin.approve')")
    public ResponseEntity<FeedbackResetResponse> resetFeedbackScopeState(FeedbackScopeLayer scopeLayer, UUID scopeId, FeedbackResetRequest feedbackResetRequest) {
        FeedbackResetResult result = feedbackService.resetScope(scopeLayer.getValue(), scopeId, feedbackResetRequest.getActorId());
        return ResponseEntity.ok(new FeedbackResetResponse()
                .scopeLayer(scopeLayer)
                .scopeId(scopeId)
                .actorId(result.actorId())
                .auditReference(result.auditReference())
                .resetAt(OffsetDateTime.ofInstant(result.resetAt(), ZoneOffset.UTC)));
    }

    private FeedbackEventResponse toResponse(FeedbackEventRecord saved) {
        return new FeedbackEventResponse(saved.feedbackEventId(), saved.setlistId(), saved.arrangementId(),
                com.cadentia.generated.model.FeedbackOutcome.fromValue(saved.outcome()),
                FeedbackScopeLayer.fromValue(saved.scopeLayer()), saved.scopeId(),
                OffsetDateTime.ofInstant(saved.createdAt(), ZoneOffset.UTC))
                .setlistVersionId(saved.setlistVersionId())
                .actorId(saved.actorId())
                .replacementReason(saved.replacementReason() == null ? null : com.cadentia.generated.model.FeedbackReplacementReason.fromValue(saved.replacementReason()))
                .replacedWithArrangementId(saved.replacedWithArrangementId())
                .familiarityScore(saved.familiarityScore());
    }

    private Map<String, Long> toLongMap(Map<String, Integer> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return source.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().longValue()));
    }
}

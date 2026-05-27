package com.cadentia.feedback;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class FeedbackModels {

    private FeedbackModels() {
    }

    public record FeedbackEventRecord(
            UUID feedbackEventId,
            UUID setlistId,
            UUID setlistVersionId,
            UUID arrangementId,
            String outcome,
            String scopeLayer,
            UUID scopeId,
            String actorId,
            String replacementReason,
            UUID replacedWithArrangementId,
            Integer familiarityScore,
            Instant createdAt) {
    }

    public record FeedbackScopeAggregate(
            String scopeLayer,
            UUID scopeId,
            int acceptedCount,
            int rejectedCount,
            int skippedCount,
            int favoritedCount,
            Map<String, Integer> replacementReasonCounts,
            Instant lastFeedbackAt) {
    }

    public record FeedbackResetResult(String scopeLayer, UUID scopeId, String actorId, Instant resetAt, String auditReference) {
    }
}

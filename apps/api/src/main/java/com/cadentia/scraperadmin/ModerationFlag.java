package com.cadentia.scraperadmin;

import java.time.Instant;
import java.util.UUID;

public record ModerationFlag(
        UUID id,
        UUID importCandidateId,
        ModerationFlagType type,
        ModerationFlagSeverity severity,
        ModerationFlagStatus status,
        String policyVersion,
        String policyRuleId,
        String openedBy,
        String assignedTo,
        String resolutionNotes,
        boolean excludeFromRecommendation,
        Instant openedAt,
        Instant updatedAt) {
}

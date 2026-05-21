package com.cadentia.scraperadmin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdminAuditEvent(
        UUID id,
        UUID entityId,
        String entityType,
        String action,
        String actor,
        Instant occurredAt,
        String reason,
        Map<String, Object> beforeState,
        Map<String, Object> afterState) {
}

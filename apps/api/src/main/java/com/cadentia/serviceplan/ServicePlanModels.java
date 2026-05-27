package com.cadentia.serviceplan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ServicePlanModels {
    private ServicePlanModels() {}

    public enum ServicePlanStatus { DRAFT, PUBLISHED, FINALIZED }

    public record ServicePlanBlock(
            UUID blockId,
            String blockType,
            int positionIndex,
            UUID arrangementId,
            String serviceNotes,
            String overrideKey,
            String overrideMode,
            UUID sourceSetlistVersionId,
            UUID sourceSetlistItemId) {}

    public record SetlistAttachment(UUID attachmentId, UUID setlistId, UUID setlistVersionId, int attachmentOrder) {}

    public record ServicePlanRecord(
            UUID servicePlanId,
            Instant serviceDateTime,
            String title,
            String theme,
            String scripture,
            String notes,
            ServicePlanStatus status,
            Instant publishedAt,
            String publishedBy,
            List<ServicePlanBlock> blocks,
            List<SetlistAttachment> attachments) {}
}

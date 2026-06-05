package com.cadentia.serviceplan;

import com.cadentia.generated.model.ReadinessSummary;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public final class ServicePlanModels {

    private ServicePlanModels() {
    }

    public enum ServicePlanStatus {
        DRAFT,
        PUBLISHED,
        FINALIZED
    }

    public enum ReadinessStatus {
        UNKNOWN,
        READY,
        AT_RISK,
        BLOCKED
    }

    public record OperationalReadinessSummary(
            ReadinessStatus status,
            List<String> objectiveBlockers,
            List<String> missingPeople,
            List<String> unresolvedArrangementConflicts,
            int privateNoteCount,
            Instant lastUpdatedAt) {

        public static OperationalReadinessSummary unknown() {
            return new OperationalReadinessSummary(ReadinessStatus.UNKNOWN, List.of(), List.of(), List.of(), 0, null);
        }

        public ReadinessSummary toReadinessSummary() {
            ReadinessSummary response = new ReadinessSummary(
                    com.cadentia.generated.model.ReadinessStatus.fromValue(status().name()),
                    objectiveBlockers(),
                    missingPeople(),
                    unresolvedArrangementConflicts(),
                    privateNoteCount());
            if (lastUpdatedAt() != null) {
                response.setLastUpdatedAt(OffsetDateTime.ofInstant(lastUpdatedAt(), ZoneOffset.UTC));
            }
            return response;
        }
    }

    public record ServicePlanBlock(
            UUID blockId,
            String blockType,
            int positionIndex,
            UUID arrangementId,
            String serviceNotes,
            String overrideKey,
            String overrideMode,
            UUID sourceSetlistVersionId,
            UUID sourceSetlistItemId) {
    }

    public record SetlistAttachment(
            UUID attachmentId,
            UUID setlistId,
            UUID setlistVersionId,
            int attachmentOrder) {
    }

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
            List<SetlistAttachment> attachments,
            OperationalReadinessSummary readinessSummary) {

        public ServicePlanRecord(
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
                List<SetlistAttachment> attachments) {
            this(servicePlanId, serviceDateTime, title, theme, scripture, notes, status, publishedAt, publishedBy,
                    blocks, attachments, OperationalReadinessSummary.unknown());
        }
    }
}

package com.cadentia.reng.setlist;

import com.cadentia.serviceplan.ServicePlanModels.OperationalReadinessSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SetlistVersionModels {

    private SetlistVersionModels() {}

    public record CreateSetlistBaselineCommand(
            String createdBy,
            String scoringProfileVersion,
            String engineVersion,
            String requestPayload,
            String parsedIntentPayload,
            String explanationFactsPayload,
            List<CreateSetlistItemCommand> items,
            String lineagePolicy) {}

    public record CreateSetlistVersionCommand(
            UUID setlistId,
            UUID parentVersionId,
            String createdBy,
            String scoringProfileVersion,
            String engineVersion,
            String requestPayload,
            String parsedIntentPayload,
            String explanationFactsPayload,
            String commitSummary,
            List<CreateSetlistItemCommand> items,
            List<SetlistEditEventCommand> editEvents) {}

    public record CreateSetlistItemCommand(
            int positionIndex,
            UUID catalogArrangementId,
            String transposedKey,
            String transposedMode,
            UUID sourceItemId,
            String itemProvenance,
            String notes) {}

    public record SetlistEditEventCommand(
            int eventIndex,
            String eventType,
            UUID itemId,
            Integer fromPosition,
            Integer toPosition,
            UUID replacementArrangementId,
            String transposeToKey,
            String transposeToMode,
            Boolean removed,
            String payload) {}

    public record SetlistVersionSnapshot(
            UUID setlistId,
            UUID versionId,
            UUID parentVersionId,
            int versionNumber,
            String provenanceType,
            String scoringProfileVersion,
            String engineVersion,
            String requestPayload,
            String parsedIntentPayload,
            String explanationFactsPayload,
            Instant createdAt,
            String createdBy,
            String commitSummary,
            List<SetlistVersionItemSnapshot> items,
            OperationalReadinessSummary readinessSummary) {

        public SetlistVersionSnapshot(
                UUID setlistId,
                UUID versionId,
                UUID parentVersionId,
                int versionNumber,
                String provenanceType,
                String scoringProfileVersion,
                String engineVersion,
                Instant createdAt,
                String createdBy,
                List<SetlistVersionItemSnapshot> items) {
            this(setlistId, versionId, parentVersionId, versionNumber, provenanceType, scoringProfileVersion,
                    engineVersion, "{}", "{}", "[]", createdAt, createdBy, null, items,
                    OperationalReadinessSummary.unknown());
        }

        public SetlistVersionSnapshot(
                UUID setlistId,
                UUID versionId,
                UUID parentVersionId,
                int versionNumber,
                String provenanceType,
                String scoringProfileVersion,
                String engineVersion,
                String requestPayload,
                String parsedIntentPayload,
                String explanationFactsPayload,
                Instant createdAt,
                String createdBy,
                List<SetlistVersionItemSnapshot> items) {
            this(setlistId, versionId, parentVersionId, versionNumber, provenanceType, scoringProfileVersion,
                    engineVersion, requestPayload, parsedIntentPayload, explanationFactsPayload, createdAt,
                    createdBy, null, items, OperationalReadinessSummary.unknown());
        }

        public SetlistVersionSnapshot(
                UUID setlistId,
                UUID versionId,
                UUID parentVersionId,
                int versionNumber,
                String provenanceType,
                String scoringProfileVersion,
                String engineVersion,
                String requestPayload,
                String parsedIntentPayload,
                String explanationFactsPayload,
                Instant createdAt,
                String createdBy,
                String commitSummary,
                List<SetlistVersionItemSnapshot> items) {
            this(setlistId, versionId, parentVersionId, versionNumber, provenanceType, scoringProfileVersion,
                    engineVersion, requestPayload, parsedIntentPayload, explanationFactsPayload, createdAt,
                    createdBy, commitSummary, items, OperationalReadinessSummary.unknown());
        }
    }

    public record SetlistVersionItemSnapshot(
            UUID id,
            int positionIndex,
            UUID catalogArrangementId,
            String transposedKey,
            String transposedMode,
            UUID sourceItemId,
            String itemProvenance,
            String notes) {}
}

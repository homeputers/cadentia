package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ControlledVocabularyEntry;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueActionStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueActionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalNoteRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalSessionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RehearsalWorkflowRepository {

    List<ControlledVocabularyEntry> listReadinessStates();

    List<ControlledVocabularyEntry> listIssueCategories();

    List<ControlledVocabularyEntry> listIssueStatuses();

    RehearsalSessionRecord createSession(
            UUID servicePlanId,
            String sessionCode,
            Instant startsAt,
            Instant endsAt,
            String location,
            String createdBy);

    RehearsalSessionRecord archiveSession(UUID servicePlanId, UUID rehearsalSessionId, String archivedBy);

    void recordServiceReadiness(
            UUID servicePlanId,
            UUID rehearsalSessionId,
            ReadinessStateCode newStateCode,
            String rationale,
            String changedBy);

    RehearsalNoteRecord addNote(
            UUID servicePlanId,
            RehearsalTarget target,
            String noteBody,
            String visibilityCode,
            String createdBy);

    RehearsalIssueRecord createIssue(
            UUID servicePlanId,
            RehearsalTarget target,
            IssueCategoryCode categoryCode,
            IssueSeverityCode severityCode,
            IssueStatusCode statusCode,
            String title,
            String detail,
            String detectedBy);

    RehearsalIssueActionRecord addIssueAction(
            UUID servicePlanId,
            UUID issueId,
            IssueActionStatusCode statusCode,
            String actionSummary,
            IssueOwnerType ownerType,
            String ownerActor,
            String ownerTeamRoleCode,
            UUID ownerServiceAssignmentId,
            String createdBy);

    ArrangementOverrideRecord createArrangementOverride(ArrangementOverrideRecord overrideRecord);
}

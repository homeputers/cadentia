package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ControlledVocabularyEntry;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueActionStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalAuditRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueActionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalNoteRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalSessionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RehearsalWorkflowRepository {

    List<ControlledVocabularyEntry> listReadinessStates();

    List<ControlledVocabularyEntry> listIssueCategories();

    List<ControlledVocabularyEntry> listIssueStatuses();

    Optional<ReadinessStateCode> findServiceReadiness(UUID servicePlanId);

    RehearsalSessionRecord createSession(
            UUID servicePlanId,
            String sessionCode,
            Instant startsAt,
            Instant endsAt,
            String location,
            String createdBy);

    RehearsalSessionRecord updateSession(
            UUID servicePlanId,
            UUID rehearsalSessionId,
            String sessionCode,
            Instant startsAt,
            Instant endsAt,
            String location,
            String updatedBy);

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

    Optional<RehearsalIssueRecord> findIssue(UUID servicePlanId, UUID issueId);

    List<RehearsalIssueRecord> listIssues(UUID servicePlanId);

    List<RehearsalIssueActionRecord> listIssueActions(UUID servicePlanId);

    RehearsalIssueRecord createIssue(
            UUID servicePlanId,
            RehearsalTarget target,
            IssueCategoryCode categoryCode,
            IssueSeverityCode severityCode,
            IssueStatusCode statusCode,
            String title,
            String detail,
            String detectedBy);

    RehearsalIssueRecord updateIssueSeverity(
            UUID servicePlanId,
            UUID issueId,
            IssueSeverityCode severityCode,
            String updatedBy);

    RehearsalIssueRecord updateIssueStatus(
            UUID servicePlanId,
            UUID issueId,
            IssueStatusCode statusCode,
            String updatedBy);

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

    RehearsalIssueActionRecord updateIssueActionOwner(
            UUID servicePlanId,
            UUID actionId,
            IssueOwnerType ownerType,
            String ownerActor,
            String ownerTeamRoleCode,
            UUID ownerServiceAssignmentId,
            String updatedBy);

    RehearsalIssueActionRecord updateIssueActionStatus(
            UUID servicePlanId,
            UUID actionId,
            IssueActionStatusCode statusCode,
            String updatedBy);

    ArrangementOverrideRecord createArrangementOverride(ArrangementOverrideRecord overrideRecord);

    RehearsalAuditRecord recordAudit(RehearsalAuditRecord auditRecord);
}

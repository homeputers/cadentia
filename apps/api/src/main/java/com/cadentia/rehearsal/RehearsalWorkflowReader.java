package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueActionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalSessionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowStatus;
import java.util.List;
import java.util.UUID;

public interface RehearsalWorkflowReader {

    List<RehearsalSessionRecord> listSessions(UUID servicePlanId);

    List<RehearsalIssueRecord> listIssues(UUID servicePlanId);

    List<RehearsalIssueActionRecord> listIssueActions(UUID servicePlanId);

    List<ArrangementOverrideRecord> listArrangementOverrides(UUID servicePlanId);

    WorkflowStatus workflowStatus(UUID servicePlanId);
}

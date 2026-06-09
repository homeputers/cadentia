package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowSummary;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowSummaryAudience;
import java.util.UUID;

public interface RehearsalWorkflowSummaryProvider {

    WorkflowSummary summarize(UUID servicePlanId, WorkflowSummaryAudience audience);
}

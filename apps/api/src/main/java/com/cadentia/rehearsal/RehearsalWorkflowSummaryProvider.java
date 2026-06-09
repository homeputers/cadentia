package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummary;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummaryAudience;
import java.util.UUID;

public interface RehearsalWorkflowSummaryProvider {

    RehearsalWorkflowSummary summarize(UUID servicePlanId, RehearsalWorkflowSummaryAudience audience);
}

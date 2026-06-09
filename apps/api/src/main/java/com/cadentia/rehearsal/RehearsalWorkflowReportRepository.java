package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalCompletedServiceHistoryRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalReportActionRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalReportIssueRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalReportServiceRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalRetentionArchiveResult;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalRetentionConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RehearsalWorkflowReportRepository {

    List<RehearsalReportServiceRow> listServicesBlockedFromReadiness(Instant asOf);

    List<RehearsalReportIssueRow> listOpenBlockersByService(UUID servicePlanId);

    List<RehearsalReportIssueRow> listUnresolvedTransitionIssues(UUID servicePlanId);

    List<RehearsalReportIssueRow> listDifficultSongs(UUID servicePlanId);

    List<RehearsalReportActionRow> listOverdueOwnerActions(Instant asOf, UUID servicePlanId);

    List<RehearsalReportServiceRow> listServicesWithActiveArrangementOverrides();

    List<RehearsalCompletedServiceHistoryRow> listCompletedServiceHistory(Instant completedSince, Instant completedBefore);

    RehearsalRetentionArchiveResult archiveCompletedRehearsalData(
            RehearsalRetentionConfiguration retentionConfiguration,
            Instant asOf,
            String archivedBy);
}

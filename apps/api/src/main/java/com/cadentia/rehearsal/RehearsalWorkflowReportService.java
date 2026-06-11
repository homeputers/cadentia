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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RehearsalWorkflowReportService {

    private final RehearsalWorkflowReportRepository reportRepository;
    private final RehearsalWorkflowAuthorizationPolicy authorizationPolicy;
    private final RehearsalWorkflowTelemetryRecorder telemetryRecorder;

    public RehearsalWorkflowReportService(
            RehearsalWorkflowReportRepository reportRepository,
            RehearsalWorkflowAuthorizationPolicy authorizationPolicy,
            RehearsalWorkflowTelemetryRecorder telemetryRecorder) {
        this.reportRepository = reportRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.telemetryRecorder = telemetryRecorder;
    }

    public List<RehearsalReportServiceRow> servicesBlockedFromReadiness(Instant asOf) {
        authorizationPolicy.requireWorkflowReporting();
        return reportRepository.listServicesBlockedFromReadiness(asOf);
    }

    public List<RehearsalReportIssueRow> openBlockers(UUID servicePlanId) {
        authorizationPolicy.requireWorkflowReporting();
        return reportRepository.listOpenBlockersByService(servicePlanId);
    }

    public List<RehearsalReportIssueRow> unresolvedTransitionIssues(UUID servicePlanId) {
        authorizationPolicy.requireWorkflowReporting();
        return reportRepository.listUnresolvedTransitionIssues(servicePlanId);
    }

    public List<RehearsalReportIssueRow> difficultSongs(UUID servicePlanId) {
        authorizationPolicy.requireWorkflowReporting();
        return reportRepository.listDifficultSongs(servicePlanId);
    }

    public List<RehearsalReportActionRow> overdueOwnerActions(Instant asOf, UUID servicePlanId) {
        authorizationPolicy.requireWorkflowReporting();
        return reportRepository.listOverdueOwnerActions(asOf, servicePlanId);
    }

    public List<RehearsalReportServiceRow> servicesWithActiveArrangementOverrides() {
        authorizationPolicy.requireWorkflowReporting();
        return reportRepository.listServicesWithActiveArrangementOverrides();
    }

    public List<RehearsalCompletedServiceHistoryRow> completedServiceHistory(
            Instant completedSince,
            Instant completedBefore) {
        authorizationPolicy.requireWorkflowReporting();
        return reportRepository.listCompletedServiceHistory(completedSince, completedBefore);
    }

    @Transactional
    public RehearsalRetentionArchiveResult archiveCompletedRehearsalData(
            RehearsalRetentionConfiguration retentionConfiguration,
            Instant asOf) {
        authorizationPolicy.requireEmergencyCorrection();
        RehearsalRetentionArchiveResult result = reportRepository.archiveCompletedRehearsalData(
                retentionConfiguration, asOf, authorizationPolicy.currentActor());
        telemetryRecorder.recordOverrideChanged("archive_retention", "archived");
        return result;
    }
}

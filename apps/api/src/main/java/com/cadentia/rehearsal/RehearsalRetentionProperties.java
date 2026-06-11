package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalRetentionConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cadentia.rehearsal.retention")
public class RehearsalRetentionProperties {

    private int completedSessionsRetainDays = 400;
    private int notesRetainDays = 180;
    private int issuesRetainDays = 400;
    private int overridesRetainDays = 400;
    private int auditRetainDays = 2555;
    private int minCompletedSessionsRetainDays = 90;
    private int minNotesRetainDays = 30;
    private int minIssuesRetainDays = 180;
    private int minOverridesRetainDays = 180;
    private int minAuditRetainDays = 2555;

    public RehearsalRetentionConfiguration toConfiguration() {
        return new RehearsalRetentionConfiguration(
                completedSessionsRetainDays,
                notesRetainDays,
                issuesRetainDays,
                overridesRetainDays,
                auditRetainDays,
                minCompletedSessionsRetainDays,
                minNotesRetainDays,
                minIssuesRetainDays,
                minOverridesRetainDays,
                minAuditRetainDays);
    }

    public void setCompletedSessionsRetainDays(int completedSessionsRetainDays) {
        this.completedSessionsRetainDays = completedSessionsRetainDays;
    }

    public void setNotesRetainDays(int notesRetainDays) {
        this.notesRetainDays = notesRetainDays;
    }

    public void setIssuesRetainDays(int issuesRetainDays) {
        this.issuesRetainDays = issuesRetainDays;
    }

    public void setOverridesRetainDays(int overridesRetainDays) {
        this.overridesRetainDays = overridesRetainDays;
    }

    public void setAuditRetainDays(int auditRetainDays) {
        this.auditRetainDays = auditRetainDays;
    }

    public void setMinCompletedSessionsRetainDays(int minCompletedSessionsRetainDays) {
        this.minCompletedSessionsRetainDays = minCompletedSessionsRetainDays;
    }

    public void setMinNotesRetainDays(int minNotesRetainDays) {
        this.minNotesRetainDays = minNotesRetainDays;
    }

    public void setMinIssuesRetainDays(int minIssuesRetainDays) {
        this.minIssuesRetainDays = minIssuesRetainDays;
    }

    public void setMinOverridesRetainDays(int minOverridesRetainDays) {
        this.minOverridesRetainDays = minOverridesRetainDays;
    }

    public void setMinAuditRetainDays(int minAuditRetainDays) {
        this.minAuditRetainDays = minAuditRetainDays;
    }
}

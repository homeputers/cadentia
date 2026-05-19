package com.cadentia.songimport;

import com.cadentia.catalog.model.ImportCandidateStatus;

public record ImportOperationalMetrics(
        int stagedCandidates,
        int blockedCandidates,
        int failedCandidates,
        int duplicateSuspectedCandidates,
        int reviewReadyCandidates) {

    public static ImportOperationalMetrics from(ImportJobRecord jobRecord) {
        int duplicateSuspected = 0;
        int reviewReady = 0;
        for (StagedImportCandidate stagedCandidate : jobRecord.stagedCandidates()) {
            if (stagedCandidate.status() == ImportCandidateStatus.DEDUPLICATION_REVIEW) {
                duplicateSuspected++;
            }
            if (stagedCandidate.status() == ImportCandidateStatus.READY_TO_MERGE) {
                reviewReady++;
            }
        }

        int blocked = jobRecord.status() == ImportJobStatus.POLICY_BLOCKED ? 1 : 0;
        int failed = jobRecord.sourceFailures().size();
        return new ImportOperationalMetrics(jobRecord.stagedCandidates().size(), blocked, failed, duplicateSuspected, reviewReady);
    }
}

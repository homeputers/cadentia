package com.cadentia.songimport;

public enum ImportJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    FAILED,
    CANCELED,
    POLICY_BLOCKED,
    RETRY_SCHEDULED
}

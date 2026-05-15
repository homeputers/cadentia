package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.ImportBatchStatus;
import java.time.Instant;
import java.util.UUID;

public record ImportBatch(
        UUID id,
        String sourceSystem,
        String initiatedBy,
        ImportBatchStatus status,
        String summaryJson,
        Instant startedAt,
        Instant completedAt) {
}

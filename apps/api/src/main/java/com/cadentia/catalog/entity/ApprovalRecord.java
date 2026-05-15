package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import java.time.Instant;
import java.util.UUID;

public record ApprovalRecord(
        UUID id,
        UUID songId,
        UUID arrangementId,
        UUID lyricsDocumentId,
        ApprovalType approvalType,
        ApprovalStatus status,
        String reviewer,
        String reviewNotes,
        Instant reviewedAt,
        Instant createdAt) {
}
